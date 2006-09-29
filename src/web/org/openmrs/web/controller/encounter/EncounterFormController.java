package org.openmrs.web.controller.encounter;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Encounter;
import org.openmrs.EncounterType;
import org.openmrs.Form;
import org.openmrs.FormField;
import org.openmrs.Location;
import org.openmrs.Obs;
import org.openmrs.api.EncounterService;
import org.openmrs.api.FormService;
import org.openmrs.api.context.Context;
import org.openmrs.util.OpenmrsConstants;
import org.openmrs.web.WebConstants;
import org.openmrs.web.propertyeditor.EncounterTypeEditor;
import org.openmrs.web.propertyeditor.FormEditor;
import org.openmrs.web.propertyeditor.LocationEditor;
import org.springframework.beans.propertyeditors.CustomDateEditor;
import org.springframework.beans.propertyeditors.CustomNumberEditor;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;
import org.springframework.web.bind.ServletRequestDataBinder;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.SimpleFormController;
import org.springframework.web.servlet.view.RedirectView;

public class EncounterFormController extends SimpleFormController {
	
    /** Logger for this class and subclasses */
    protected final Log log = LogFactory.getLog(getClass());
    
    SimpleDateFormat dateFormat;
    
	/**
	 * 
	 * Allows for Integers to be used as values in input tags.
	 *   Normally, only strings and lists are expected 
	 * 
	 * @see org.springframework.web.servlet.mvc.BaseCommandController#initBinder(javax.servlet.http.HttpServletRequest, org.springframework.web.bind.ServletRequestDataBinder)
	 */
	protected void initBinder(HttpServletRequest request, ServletRequestDataBinder binder) throws Exception {
		super.initBinder(request, binder);

		dateFormat = new SimpleDateFormat(OpenmrsConstants.OPENMRS_LOCALE_DATE_PATTERNS().get(Context.getLocale().toString().toLowerCase()), Context.getLocale());
		
        binder.registerCustomEditor(java.lang.Integer.class,
                new CustomNumberEditor(java.lang.Integer.class, true));
        binder.registerCustomEditor(java.util.Date.class, 
        		new CustomDateEditor(dateFormat, true));
        binder.registerCustomEditor(EncounterType.class, new EncounterTypeEditor());
        binder.registerCustomEditor(Location.class, new LocationEditor());
        binder.registerCustomEditor(Form.class, new FormEditor());
	}

	protected ModelAndView processFormSubmission(HttpServletRequest request, HttpServletResponse reponse, Object obj, BindException errors) throws Exception {
		
		Encounter encounter = (Encounter)obj;
		
		Context.addProxyPrivilege(OpenmrsConstants.PRIV_VIEW_USERS);
		Context.addProxyPrivilege(OpenmrsConstants.PRIV_VIEW_PATIENTS);
		try {
			if (Context.isAuthenticated()) {
				if (request.getParameter("patientId") != null)
					encounter.setPatient(Context.getPatientService().getPatient(Integer.valueOf(request.getParameter("patientId"))));
				if (request.getParameter("providerId") != null)
					encounter.setProvider(Context.getUserService().getUser(Integer.valueOf(request.getParameter("providerId"))));
				if (encounter.isVoided())
					ValidationUtils.rejectIfEmptyOrWhitespace(errors, "voidReason", "error.null");
				
			}
		} finally {
			Context.removeProxyPrivilege(OpenmrsConstants.PRIV_VIEW_USERS);
			Context.removeProxyPrivilege(OpenmrsConstants.PRIV_VIEW_PATIENTS);
		}
		
		return super.processFormSubmission(request, reponse, encounter, errors);
	}

	/** 
	 * 
	 * The onSubmit function receives the form/command object that was modified
	 *   by the input form and saves it to the db
	 * 
	 * @see org.springframework.web.servlet.mvc.SimpleFormController#onSubmit(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse, java.lang.Object, org.springframework.validation.BindException)
	 */
	protected ModelAndView onSubmit(HttpServletRequest request, HttpServletResponse response, Object obj, BindException errors) throws Exception {
		
		HttpSession httpSession = request.getSession();
		
		String view = getFormView();

		Context.addProxyPrivilege(OpenmrsConstants.PRIV_VIEW_USERS);
		Context.addProxyPrivilege(OpenmrsConstants.PRIV_VIEW_PATIENTS);
		try {
			if (Context.isAuthenticated()) {
				Encounter encounter = (Encounter)obj;
				
				// if this is a new encounter, they can specify a patient.  add it
				if (request.getParameter("patientId") != null)
					encounter.setPatient(Context.getPatientService().getPatient(Integer.valueOf(request.getParameter("patientId"))));
				
				// set the provider if they changed it
				encounter.setProvider(Context.getUserService().getUser(Integer.valueOf(request.getParameter("providerId"))));
				
				if (encounter.isVoided() && encounter.getVoidedBy() == null)
					// if this is a "new" voiding, call voidEncounter to set appropriate attributes
					Context.getEncounterService().voidEncounter(encounter, encounter.getVoidReason());
				else if (!encounter.isVoided() && encounter.getVoidedBy() != null)
					// if this was just unvoided, call unvoidEncounter to unset appropriate attributes
					Context.getEncounterService().unvoidEncounter(encounter);
				else
					Context.getEncounterService().updateEncounter(encounter);
				
				view = getSuccessView();
				view = view + "?encounterId=" + encounter.getEncounterId();
				
				httpSession.setAttribute(WebConstants.OPENMRS_MSG_ATTR, "Encounter.saved");
			}
		} finally {
			Context.removeProxyPrivilege(OpenmrsConstants.PRIV_VIEW_USERS);
			Context.removeProxyPrivilege(OpenmrsConstants.PRIV_VIEW_PATIENTS);
		}
		
		return new ModelAndView(new RedirectView(view));
	}

	/**
	 * 
	 * This is called prior to displaying a form for the first time.  It tells Spring
	 *   the form/command object to load into the request
	 * 
	 * @see org.springframework.web.servlet.mvc.AbstractFormController#formBackingObject(javax.servlet.http.HttpServletRequest)
	 */
    protected Object formBackingObject(HttpServletRequest request) throws ServletException {

		Encounter encounter = null;
		
		if (Context.isAuthenticated()) {
			EncounterService es = Context.getEncounterService();
			String encounterId = request.getParameter("encounterId");
	    	if (encounterId != null) {
	    		encounter = es.getEncounter(Integer.valueOf(encounterId));
	    		//encounter.getObs();
	    	}
		}
		
		if (encounter == null)
			encounter = new Encounter();
    	
        return encounter;
    }

	protected Map referenceData(HttpServletRequest request, Object obj, Errors error) throws Exception {
		
		Encounter encounter = (Encounter)obj;
		
		Map<String, Object> map = new HashMap<String, Object>();
		List<Integer> editedObs = new Vector<Integer>();

		// The map returned to the form
		Map<Obs, FormField> obsMap = new HashMap<Obs, FormField>();
		// User for sorting
		Map<Obs, FormField> obsMapTemp = new HashMap<Obs, FormField>();
		
		// temporary list to hold the sorted obs
		List<FormField> formFields = new Vector<FormField>();
		
		List<Obs> observations = new Vector<Obs>();
		
		Form form = encounter.getForm();
		
		if (Context.isAuthenticated()) {
			EncounterService es = Context.getEncounterService();
			FormService fs = Context.getFormService();
			
			map.put("encounterTypes", es.getEncounterTypes());
			map.put("forms", Context.getFormService().getForms());
			// loop over the encounter's observations to find the edited obs
			String reason = "";
			if (encounter.getObs() != null && !encounter.getObs().isEmpty()) {
				for (Obs o : encounter.getObs()) {
					// only the voided obs have been edited
					if (o.isVoided()){
						// assumes format of: ".* (new obsId: \d*)"
						reason = o.getVoidReason();
						int start = reason.lastIndexOf(" ") + 1;
						int end = reason.length() - 1;
						try {
							reason = reason.substring(start, end);
							editedObs.add(Integer.valueOf(reason));
						} catch (Exception e) {}
					}
					
					// populate the obs map so we can 
					//  1) sort the obs according to FormField
					//  2) look up the formField by the obs object
					FormField ff = fs.getFormField(form, o.getConcept());
					if (ff == null)
						ff = new FormField();
					formFields.add(ff);
					obsMap.put(o, ff);
					obsMapTemp.put(o, ff);
				}
				
				try {
					// sort the temp list according the the FormFields.compare() method
					Collections.sort(formFields, new FormFieldComparator());
				}
				catch (Exception e) {
					log.error("Error while sorting obs for encounter: " + encounter, e);
				}
				
				// loop over the sorted formFields to add the corresponding
				//  obs to the returned obs list
				for (FormField f : formFields) {
					observations.add(popObsFromMap(obsMapTemp, f));
				}
			}
		}
		
		log.debug("setting sorted observations in page context (size: " + observations.size() + ")");
		map.put("observations", observations);
		
		log.debug("setting obsMap in page context (size: " + obsMap.size() + ")");
		map.put("obsMap", obsMap);
		
		log.debug("setting datePattern in page context");
		map.put("datePattern", dateFormat.toLocalizedPattern().toLowerCase());
		
		log.debug("setting locale in page context: " + Context.getLocale());
		map.put("locale", Context.getLocale());
		
		log.debug("setting edited obs in page context: " + editedObs);
		map.put("editedObs", editedObs);
		
		return map;
	}
    
	/**
	 * Searches the given map for the given FormField
	 * 
	 * @param map
	 * @param f
	 * @return
	 */
	private Obs popObsFromMap(Map<Obs, FormField> map, FormField f) {
		for (Map.Entry<Obs, FormField> entry : map.entrySet()) {
			if (entry.getValue() == f) {
				Obs o = entry.getKey();
				map.remove(o);
				return o;
			}
		}
		
		return null;
	}
	
	/**
	 * Internal class used to sort FormField first according to the parent FormFieldId
	 * then by FormField.compare()
	 */
	private class FormFieldComparator implements Comparator<FormField> {
		public int compare(FormField ff1, FormField ff2) {
			if (ff1.getParent().equals(ff2.getParent())) {
				return ff1.compareTo(ff2);
			}
			else if (ff1.getParent() == null && ff2.getParent() == null)
				return 0;
			else {
				// search upwards until we have siblings
				// this algorithm is O(depth squared) -- if we end up having 
				// deep trees, might want to change it 
				
				// get arrays of ancestors
				List<FormField> ff1Parents = new Vector<FormField>();
				while (ff1 != null) {
					ff1Parents.add(ff1);
					ff1 = ff1.getParent();
				}
				
				List<FormField> ff2Parents = new Vector<FormField>();
				while (ff2 != null) {
					ff2Parents.add(ff2);
					ff2 = ff2.getParent();
				}
				
				for (int i = 1; i < ff1Parents.size(); i++) {
					FormField ff1Parent = ff1Parents.get(i); 
					for (int j = 1; j < ff2Parents.size(); j++) {
						if (ff1Parent.equals(ff2Parents.get(j))) {
							return ff1Parents.get(i-1).compareTo(ff2Parents.get(j-1));
						}
					}
				}
				
				return ff1Parents.get(ff1Parents.size()-1).compareTo(ff2Parents.get(ff2Parents.size()-1)); 
			}
		}
	}
	
}
	