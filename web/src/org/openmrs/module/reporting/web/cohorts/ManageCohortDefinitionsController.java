package org.openmrs.module.reporting.web.cohorts;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Cohort;
import org.openmrs.api.context.Context;
import org.openmrs.module.cohort.definition.CohortDefinition;
import org.openmrs.module.cohort.definition.configuration.Property;
import org.openmrs.module.cohort.definition.service.CohortDefinitionService;
import org.openmrs.module.cohort.definition.util.CohortDefinitionUtil;
import org.openmrs.module.evaluation.EvaluationContext;
import org.openmrs.module.evaluation.parameter.Parameter;
import org.openmrs.module.reporting.web.widget.handler.WidgetHandler;
import org.openmrs.module.util.ReflectionUtil;
import org.openmrs.util.HandlerUtil;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class ManageCohortDefinitionsController {
	
	protected static Log log = LogFactory.getLog(ManageCohortDefinitionsController.class);
	
	/**
	 * Lists the cohort definitions.
	 * 
	 * @param includeRetired
	 * @param model
	 * @return
	 */
    @RequestMapping("/module/reporting/cohorts/manageCohortDefinitions")
    public void manageCohortDefinitions(
    		@RequestParam(required=false, value="includeRetired") Boolean includeRetired,
    		ModelMap model) {
    	// Add all saved CohortDefinitions
    	CohortDefinitionService service = Context.getService(CohortDefinitionService.class);
    	boolean retired = includeRetired != null && includeRetired.booleanValue();
    	model.addAttribute("cohortDefinitions", service.getAllCohortDefinitions(retired));
    	
    	// Add all available CohortDefinitions
    	model.addAttribute("types", service.getCohortDefinitionTypes());
    }
    
    
    /**
     * Basically acts as the formBackingObject() method for saving a 
     * cohort definition.
     * 
     * @param uuid
     * @param type
     * @param returnUrl
     * @param model
     * @return
     */
    @RequestMapping("/module/reporting/editCohortDefinition")
    public String editCohortDefinition(
    		@RequestParam(required=false, value="uuid") String uuid,
            @RequestParam(required=false, value="type") Class<? extends CohortDefinition> type,
    		ModelMap model) {
    	
    	CohortDefinitionService service = Context.getService(CohortDefinitionService.class);
    	CohortDefinition cd = service.getCohortDefinition(uuid, type);
     	model.addAttribute("cohortDefinition", cd);
	
        return "/module/reporting/cohorts/cohortDefinitionEditor";
    }
    
    /**
     * Saves a cohort definition.
     * 
     * @param uuid
     * @param type
     * @param name
     * @param description
     * @param model
     * @return
     */
    @RequestMapping("/module/reporting/saveCohortDefinition")
    @SuppressWarnings("unchecked")
    public String saveCohortDefinition(
    		@RequestParam(required=false, value="uuid") String uuid,
            @RequestParam(required=false, value="type") Class<? extends CohortDefinition> type,
            @RequestParam(required=true, value="name") String name,
            @RequestParam(required=false, value="description") String description,
            HttpServletRequest request,
    		ModelMap model
    ) {
    	
    	CohortDefinitionService service = Context.getService(CohortDefinitionService.class);
    	    	
    	// Locate or create cohort definition
    	CohortDefinition cohortDefinition = service.getCohortDefinition(uuid, type);
    	cohortDefinition.setName(name);
    	cohortDefinition.setDescription(description);
    	cohortDefinition.getParameters().clear();
    	
    	for (Property p : CohortDefinitionUtil.getConfigurationProperties(cohortDefinition)) {
    		String fieldName = p.getField().getName();
    		String prefix = "parameter." + fieldName;
    		String valParamName =  prefix + ".value";
    		boolean isParameter = "t".equals(request.getParameter(prefix+".allowAtEvaluation"));
    		Object valToSet = null;
    		
    		Class<? extends Collection<?>> collectionType = null;
    		Class<?> fieldType = p.getField().getType();
    		
			if (ReflectionUtil.isCollection(p.getField())) {
				
				collectionType = (Class<? extends Collection<?>>)p.getField().getType();
				fieldType = (Class<?>)ReflectionUtil.getGenericTypes(p.getField())[0];
				String[] paramVals = request.getParameterValues(valParamName);
				
				if (paramVals != null) {
					Collection defaultValue = Set.class.isAssignableFrom(collectionType) ? new HashSet() : new ArrayList();
					for (String val : paramVals) {
						if (StringUtils.hasText(val)) {
							WidgetHandler h = HandlerUtil.getPreferredHandler(WidgetHandler.class, fieldType);
							defaultValue.add(h.parse(val, fieldType));
						}
					}
					valToSet = defaultValue;
				}
			}
			else {
				String paramVal = request.getParameter(valParamName);
				if (StringUtils.hasText(paramVal)) {
					WidgetHandler h = HandlerUtil.getPreferredHandler(WidgetHandler.class, fieldType);
					valToSet = h.parse(paramVal, fieldType);
				}
			}
			
			if (isParameter) {
				ReflectionUtil.setPropertyValue(cohortDefinition, p.getField(), null);
				Parameter param = new Parameter(fieldName, fieldName, fieldType, collectionType, valToSet);
				cohortDefinition.addParameter(param);
			}
			else {
				ReflectionUtil.setPropertyValue(cohortDefinition, p.getField(), valToSet);
			}
    	}
    	
    	log.warn("Saving: " + cohortDefinition);
    	Context.getService(CohortDefinitionService.class).saveCohortDefinition(cohortDefinition);

        return "redirect:/module/reporting/cohorts/manageCohortDefinitions.form";
    }

    
    /**
     * Evaluates a cohort definition given a uuid.
     * 
     * @param uuid
     * @param type
     * @param returnUrl
     * @param model
     * @return
     */
    @RequestMapping("/module/reporting/evaluateCohortDefinition")
    public String evaluateCohortDefinition(
    		@RequestParam(required=false, value="uuid") String uuid,
            @RequestParam(required=false, value="type") Class<? extends CohortDefinition> type,
    		ModelMap model) {
    	
    	CohortDefinitionService service = Context.getService(CohortDefinitionService.class);
    	CohortDefinition cohortDefinition = service.getCohortDefinition(uuid, type);
     	
    	// Evaluate the cohort definition
    	EvaluationContext context = new EvaluationContext();
    	Cohort cohort = service.evaluate(cohortDefinition, context);
    	
    	// create the model and view to return
     	model.addAttribute("cohort", cohort);
     	model.addAttribute("cohortDefinition", cohortDefinition);
     	
        return "/module/reporting/cohortDefinitionEvaluator";
    }    
    
    
    /**
     * Purges the cohort definition represented by the given uuid.
     * 
     * @param uuid
     * 		a universally unique identifier used to identify the cohort definition.
     * @return	
     * 		the name or URL that represents the view
     */
    @RequestMapping("/module/reporting/purgeCohortDefinition")
    public String purgeCohortDefinition(@RequestParam(required=false, value="uuid") String uuid) {
    	CohortDefinitionService service = 
    		Context.getService(CohortDefinitionService.class);
    	CohortDefinition cohortDefinition = service.getCohortDefinitionByUuid(uuid);
    	service.purgeCohortDefinition(cohortDefinition);	
        return "redirect:/module/reporting/cohorts/manageCohortDefinitions.form";
    }    
    

}