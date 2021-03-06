package o3erp.thrift.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import o3erp.plugin.IExpression;
import o3erp.thrift.BaseService;

import org.apache.thrift.TException;
import org.ofbiz.base.util.UtilDateTime;
import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.base.util.UtilPlugin;
import org.ofbiz.base.util.UtilProperties;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.entity.Delegator;
import org.ofbiz.entity.GenericEntityException;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.security.Security;
import org.ofbiz.service.GenericServiceException;
import org.ofbiz.service.LocalDispatcher;
import org.ofbiz.service.ModelService;
import org.ofbiz.service.ServiceContainer;
import org.ofbiz.service.ServiceUtil;

public class BaseServiceHandler implements BaseService.Iface {
	
	protected Delegator delegator;

	
	public BaseServiceHandler(Delegator delegator){
		this.delegator = delegator;
	}

	@Override
	public Map<String,String> userLogin(String loginName, String loginPwd) throws TException {
	
		Map<String,String> resultMap = new HashMap();
		if (!UtilValidate.isEmpty(loginName) && !UtilValidate.isEmpty(loginPwd)){
			try {
				LocalDispatcher dispatcher = ServiceContainer.getLocalDispatcher(this.delegator.getDelegatorName(), delegator);

				Map result = dispatcher.runSync("userLogin", UtilMisc.toMap("login.username", loginName, "login.password", loginPwd));
				if (ModelService.RESPOND_SUCCESS.equals(result.get(ModelService.RESPONSE_MESSAGE))) {

					resultMap.put(ModelService.RESPONSE_MESSAGE, ModelService.RESPOND_SUCCESS);
					String successMessage = (String)result.get(ModelService.SUCCESS_MESSAGE);
					if (UtilValidate.isEmpty(successMessage)){
						successMessage = "Login Successful";
					}
					resultMap.put(ModelService.SUCCESS_MESSAGE, successMessage);
					
					GenericValue userLogin = (GenericValue)result.get("userLogin");
					resultMap.put("partyId", userLogin.getString("partyId"));
					
				} else {
					resultMap.put(ModelService.RESPONSE_MESSAGE, ModelService.RESPOND_ERROR);
					resultMap.put(ModelService.ERROR_MESSAGE, ServiceUtil.getErrorMessage(result));
				}
			} catch (GenericServiceException e) {
				resultMap.put(ModelService.RESPONSE_MESSAGE, ModelService.RESPOND_ERROR);
				resultMap.put(ModelService.ERROR_MESSAGE, e.getMessage());
			}
		}
				
		return resultMap;
	}

	@Override
	public Map<String, String> hasPermission(String loginName, List<String> permissions) throws TException {

		LocalDispatcher dispatcher = ServiceContainer.getLocalDispatcher(this.delegator.getDelegatorName(), delegator);
		Security security = dispatcher.getSecurity();
		
		GenericValue userLogin;
		try {
			userLogin = delegator.findOne("UserLogin", true, "userLoginId", loginName);
		} catch (GenericEntityException e) {
			e.printStackTrace();
			throw new TException(e.getMessage());
		}
		
		Map<String, String> result = new HashMap();
		
		/**
		 * check if we have plugin to evaluate logical expression
		 */
		IExpression iExpr = UtilPlugin.get(IExpression.class);

		if (iExpr!=null){
			for(String permission : permissions){
				Set<String> variables = iExpr.getVariables(permission);
				Map<String,Boolean> variableMap = new HashMap();
				for(String variable:variables){
					variableMap.put(variable, security.hasPermission(variable, userLogin));
				}
				result.put(permission, iExpr.evalExpr(permission, variableMap));
			}
		} else {
			for(String permission : permissions){
				result.put(permission, security.hasPermission(permission, userLogin)?"true":"false");
			}
		}
		return result;
	}

	@Override
	public Map<String, String> hasEntityPermission(String userLoginId,	List<String> entities, List<String> actions) throws TException {

		LocalDispatcher dispatcher = ServiceContainer.getLocalDispatcher(this.delegator.getDelegatorName(), delegator);
		Security security = dispatcher.getSecurity();
		
		GenericValue userLogin;
		try {
			userLogin = delegator.findOne("UserLogin", true, "userLoginId", userLoginId);
		} catch (GenericEntityException e) {
			e.printStackTrace();
			throw new TException(e.getMessage());
		}
		
		Map<String, String> result = new HashMap();
		for(int i=0, size=entities.size(); i<size; i++){
			String entity = entities.get(i);
			String action = actions.get(i);
			result.put(entity+action, security.hasEntityPermission(entity, action, userLogin)?"true":"false");
		}
		
		return result;
	}

	@Override
	public Map<String, String> getMessageMap(String userLoginId, List<String> resourceNames) throws TException {
		LocalDispatcher dispatcher = ServiceContainer.getLocalDispatcher(this.delegator.getDelegatorName(), delegator);
		
		GenericValue userLogin;
		try {
			userLogin = delegator.findOne("UserLogin", true, "userLoginId", userLoginId);
		} catch (GenericEntityException e) {
			e.printStackTrace();
			throw new TException(e.getMessage());
		}
		
		Locale locale = null;
		String localeString = userLogin.getString("lastLocale");
        if (UtilValidate.isNotEmpty(localeString)) {
            locale =  UtilMisc.parseLocale(localeString);
        } else {
            locale = Locale.getDefault();
        }
		
		Map<String, String> result = new HashMap();
		for(String name : resourceNames){
			String[] resourceName = name.split("#");
			if (resourceName.length==1){
				result.put(name, UtilProperties.getMessage("CommonUiLabels", resourceName[0], locale));
			} else {
				result.put(name, UtilProperties.getMessage(resourceName[0], resourceName[1], locale));
			}
		}
		return result;

	}

	@Override
	public Map<String, String> callOfbizService(String userLoginId,
			String serviceName, Map<String, String> context) throws TException {

		GenericValue userLogin;
		try {
			userLogin = delegator.findOne("UserLogin", true, "userLoginId", userLoginId);
		
			LocalDispatcher dispatcher = ServiceContainer.getLocalDispatcher(this.delegator.getDelegatorName(), delegator);

			String tzId = (String)userLogin.getString("lastTimeZone");
			TimeZone timeZone = UtilDateTime.toTimeZone(tzId);
			
			Map<String,Object> serviceContext = ServiceUtil.setServiceFields(dispatcher, serviceName, (Map)context, userLogin, timeZone, Locale.getDefault());
			serviceContext.put("userLogin", userLogin);
			Map result = dispatcher.runSync(serviceName, serviceContext);
			return result;
		} catch (Exception e) {
			e.printStackTrace();
			throw new TException(e.getMessage());
		}
		
	}

}
