package org.openmrs.web.controller;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Vector;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Concept;
import org.openmrs.ConceptAnswer;
import org.openmrs.ConceptName;
import org.openmrs.ConceptNumeric;
import org.openmrs.ConceptSet;
import org.openmrs.ConceptSynonym;
import org.openmrs.Form;
import org.openmrs.api.APIException;
import org.openmrs.api.ConceptService;
import org.openmrs.api.context.Context;
import org.openmrs.web.WebConstants;
import org.openmrs.web.propertyeditor.ConceptAnswersEditor;
import org.openmrs.web.propertyeditor.ConceptClassEditor;
import org.openmrs.web.propertyeditor.ConceptDatatypeEditor;
import org.openmrs.web.propertyeditor.ConceptSetsEditor;
import org.springframework.beans.propertyeditors.CustomDateEditor;
import org.springframework.beans.propertyeditors.CustomNumberEditor;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.validation.BindException;
import org.springframework.web.bind.ServletRequestDataBinder;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.SimpleFormController;
import org.springframework.web.servlet.support.RequestContextUtils;
import org.springframework.web.servlet.view.RedirectView;

public class ConceptFormController extends SimpleFormController {
	
    /** Logger for this class and subclasses */
    protected final Log log = LogFactory.getLog(getClass());
    
	/**
	 * 
	 * Allows for other Objects to be used as values in input tags.
	 *   Normally, only strings and lists are expected 
	 * 
	 * @see org.springframework.web.servlet.mvc.BaseCommandController#initBinder(javax.servlet.http.HttpServletRequest, org.springframework.web.bind.ServletRequestDataBinder)
	 */
	protected void initBinder(HttpServletRequest request, ServletRequestDataBinder binder) throws Exception {
		super.initBinder(request, binder);
		Context context = (Context) request.getSession().getAttribute(WebConstants.OPENMRS_CONTEXT_HTTPSESSION_ATTR);
		Concept concept = null;
		String conceptId = request.getParameter("conceptId");
		
        NumberFormat nf = NumberFormat.getInstance(new Locale("en_US"));
        Locale locale = RequestContextUtils.getLocale(request);
        binder.registerCustomEditor(java.lang.Integer.class,
                new CustomNumberEditor(java.lang.Integer.class, nf, true));
		//binder.registerCustomEditor(java.lang.Integer.class, 
		//		new CustomNumberEditor(java.lang.Integer.class, true));
        binder.registerCustomEditor(java.util.Date.class, 
        		new CustomDateEditor(DateFormat.getDateInstance(DateFormat.SHORT), true));
        binder.registerCustomEditor(org.openmrs.ConceptClass.class, 
        		new ConceptClassEditor(context));
        binder.registerCustomEditor(org.openmrs.ConceptDatatype.class, 
        		new ConceptDatatypeEditor(context));
        /*binder.registerCustomEditor(java.util.Collection.class, "synonyms", 
        		new ConceptSynonymsEditor(locale)); */
        binder.registerCustomEditor(java.util.Collection.class, "conceptSets", 
        		new ConceptSetsEditor(context));
        binder.registerCustomEditor(java.util.Collection.class, "answers", 
        		new ConceptAnswersEditor(context));

	}

	/**
	 * @see org.springframework.web.servlet.mvc.AbstractFormController#processFormSubmission(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse, java.lang.Object, org.springframework.validation.BindException)
	 */
	protected ModelAndView processFormSubmission(HttpServletRequest request, HttpServletResponse response, Object object, BindException errors) throws Exception {
	
		HttpSession httpSession = request.getSession();
		Context context = (Context) httpSession.getAttribute(WebConstants.OPENMRS_CONTEXT_HTTPSESSION_ATTR);
		Concept concept = (Concept)object;
		Locale locale = RequestContextUtils.getLocale(request);
		
		if (context != null && context.isAuthenticated()) {
			
			MessageSourceAccessor msa = getMessageSourceAccessor();
			String action = request.getParameter("action");
			
			if (!action.equals(msa.getMessage("Concept.delete"))) {
				ConceptService cs = context.getConceptService();
	
				// ==== Concept Synonyms ====
					// the attribute *must* be named differently than the property, otherwise
					//   spring will modify the property as a text array
					log.debug("newSynonyms: " + request.getParameter("newSynonyms"));
					String[] tempSyns = request.getParameter("newSynonyms").split(",");
					log.debug("tempSyns: ");
					for (String s : tempSyns)
						log.debug(s);
					Collection<ConceptSynonym> originalSyns = concept.getSynonyms();
					Set<ConceptSynonym> parameterSyns = new HashSet<ConceptSynonym>();
					
					//set up parameter Synonym Set for easier add/delete functions
					// and removal of duplicates
					for (String syn : tempSyns) {
						syn = syn.trim();
						if (!syn.equals(""))
							parameterSyns.add(new ConceptSynonym(concept, syn.toUpperCase(), locale));
					}
					
					log.debug("initial originalSyns: ");
					for (ConceptSynonym s : originalSyns)
						log.debug(s);
					
					// Union the originalSyns and parameterSyns to get the 'clean' synonyms
					//   remove synonym from originalSynonym if 'clean' (already in db)
					Set<ConceptSynonym> originalSynsCopy = new HashSet<ConceptSynonym>();
					originalSynsCopy.addAll(originalSyns);
					for (ConceptSynonym o : originalSynsCopy) {
						if (o.getLocale().equals(locale.getLanguage()) &&
							!parameterSyns.contains(o)) {  // .contains() is only usable because we overrode .equals()
							originalSyns.remove(o);
						}
					}
					
					// add all new syns from parameter set
					for (ConceptSynonym p : parameterSyns) {
						if (!originalSyns.contains(p)) {  // .contains() is only usable because we overrode .equals()
							originalSyns.add(p);
						}
					}
					
					log.debug("evaluated parameterSyns: ");
					for (ConceptSynonym s : parameterSyns)
						log.debug(s);
					
					log.debug("evaluated originalSyns: ");
					for (ConceptSynonym s : originalSyns)
						log.debug(s);
	
					concept.setSynonyms(originalSyns);
					
				// ====zero out conceptSets====
					String conceptSets = request.getParameter("conceptSets");
					if (conceptSets == null)
						concept.setConceptSets(null); 
					
				// ====set concept_name properties to the correct/current locale
					String conceptName = request.getParameter("name").toUpperCase();
					if (conceptName.length() < 1)
						errors.rejectValue("name", "error.name");
					String shortName = request.getParameter("shortName");
					String description = request.getParameter("description");
					if (conceptName.length() < 1)
						errors.rejectValue("description", "error.description");
					ConceptName cn = concept.getName(locale, true);
					if (cn != null) {
						cn.setName(conceptName);
						cn.setShortName(shortName);
						cn.setDescription(description);
					}
					else {
						//TODO add description
						concept.addName(new ConceptName(conceptName, shortName, description, locale));
				}
			}
		}
		else {
			errors.reject("auth.invalid");
		}
		
		return super.processFormSubmission(request, response, concept, errors); 
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
		Context context = (Context) httpSession.getAttribute(WebConstants.OPENMRS_CONTEXT_HTTPSESSION_ATTR);
		Concept concept = (Concept)obj;
				
		if (context != null && context.isAuthenticated()) {
			
			MessageSourceAccessor msa = getMessageSourceAccessor();
			String action = request.getParameter("action");
			ConceptService cs = context.getConceptService();
			
			if (action.equals(msa.getMessage("Concept.delete"))) {
				try {
					cs.deleteConcept(concept);
					httpSession.setAttribute(WebConstants.OPENMRS_MSG_ATTR, "Concept.deleted");
					return new ModelAndView(new RedirectView("index.htm"));
				}
				catch (APIException e) {
					log.error(e);
					httpSession.setAttribute(WebConstants.OPENMRS_ERROR_ATTR, "Concept.cannot.delete");
					return new ModelAndView(new RedirectView(getSuccessView() + "?conceptId=" + concept.getConceptId().toString()));
				}
			}
			else {
			
				boolean isNew = false;
				String view = getSuccessView();
				try {
					if (concept.getConceptId() == null) {
						isNew = true;
						Integer newId = cs.getNextAvailableId();
						log.debug("new Id: " + newId);
						concept.setConceptId(newId);
						log.debug("concept id: " + concept.getConceptId());
						cs.createConcept(concept);
					}
					else {
						cs.updateConcept(concept);
					}
					httpSession.setAttribute(WebConstants.OPENMRS_MSG_ATTR, "Concept.saved");
				}
				catch (APIException e) {
					log.error(e);
					httpSession.setAttribute(WebConstants.OPENMRS_ERROR_ATTR, "Concept.cannot.save");
					if (isNew) {
						errors.reject("concept", "Concept.cannot.save");
						return new ModelAndView(new RedirectView(getSuccessView()));
					}
				}

				return new ModelAndView(new RedirectView(getSuccessView() + "?conceptId=" + concept.getConceptId().toString()));
			}
		}
		
		return new ModelAndView(new RedirectView(getFormView()));
	}

	/**
	 * 
	 * This is called prior to displaying a form for the first time.  It tells Spring
	 *   the form/command object to load into the request
	 * 
	 * @see org.springframework.web.servlet.mvc.AbstractFormController#formBackingObject(javax.servlet.http.HttpServletRequest)
	 */
    protected Object formBackingObject(HttpServletRequest request) throws ServletException {

		HttpSession httpSession = request.getSession();
		Context context = (Context) httpSession.getAttribute(WebConstants.OPENMRS_CONTEXT_HTTPSESSION_ATTR);
		
		Concept concept = null;
		
		if (context != null) {
			ConceptService cs = context.getConceptService();
			String conceptId = request.getParameter("conceptId");
	    	if (conceptId != null) {
	    		concept = cs.getConcept(Integer.valueOf(conceptId));
	    		//if (concept.isNumeric())
	    		//	concept = (ConceptNumeric)concept;
	    	}
		}
		
		if (concept == null)
			concept = new Concept();
		
		return concept;
    }
    
	/**
	 * 
	 * Called prior to form display.  Allows for data to be put 
	 * 	in the request to be used in the view
	 * 
	 * @see org.springframework.web.servlet.mvc.SimpleFormController#referenceData(javax.servlet.http.HttpServletRequest)
	 */
	protected Map referenceData(HttpServletRequest request) throws Exception {
		
		HttpSession httpSession = request.getSession();
		Context context = (Context) httpSession.getAttribute(WebConstants.OPENMRS_CONTEXT_HTTPSESSION_ATTR);
		
		Locale locale = RequestContextUtils.getLocale(request);
		Map<String, Object> map = new HashMap<String, Object>();
		
		if (context != null) {
			ConceptService cs = context.getConceptService();
			String conceptId = request.getParameter("conceptId");
			ConceptName conceptName = new ConceptName();
			Collection<ConceptSynonym> conceptSynonyms = new Vector<ConceptSynonym>();
			//Map<String, ConceptName> conceptSets = new TreeMap<String, ConceptName>();
			Map<Double, Object[]> conceptSets = new TreeMap<Double, Object[]>();
			Map<String, String> conceptAnswers = new TreeMap<String, String>();
			Collection<Form> forms = new Vector<Form>();
			
			if (conceptId != null) {
				Concept concept = cs.getConcept(Integer.valueOf(conceptId));
				
				if (concept != null) {
					// get locale specific conceptName object
					conceptName = concept.getName(locale);
					if (conceptName == null) 
						conceptName = new ConceptName();
					
					// get locale specific synonyms
					conceptSynonyms = concept.getSynonyms(locale);
		    		
					// get concept sets with locale decoded names
			    	for (ConceptSet set : concept.getConceptSets()) {
			    		Object[] arr = {set.getConcept().getConceptId().toString(), set.getConcept().getName(locale)}; 
			    		conceptSets.put(set.getSortWeight(), arr);
			    	}
					
			    	// get concept answers with locale decoded names
			    	for (ConceptAnswer answer : concept.getAnswers(true)) {
			    		log.debug("getting answers");
			    		String key = answer.getAnswerConcept().getConceptId().toString();
			    		ConceptName cn = answer.getAnswerConcept().getName(locale);
			    		String name = "";
			    		if (cn != null)
			    			name = cn.toString();
			    		if (answer.getAnswerDrug() != null) {
			    			key = key + "^" + answer.getAnswerDrug().getDrugId();
			    			name = name + " (Drug: " + answer.getAnswerDrug().getName() + ")";
			    		}
			    		if (answer.getAnswerConcept().isRetired())
			    			name = "<span class='retired'>" + name + "</span>";
			    		conceptAnswers.put(key, name);
			    	}
	
			    	//previous/next ids for links
			    	map.put("previousConcept", cs.getPrevConcept(concept));
			    	map.put("nextConcept", cs.getNextConcept(concept));
			    	forms = context.getFormService().getForms(concept);
				}
			}

	    	map.put("conceptName", conceptName);
	    	map.put("conceptSynonyms", conceptSynonyms);
	    	map.put("conceptSets", conceptSets);
	    	map.put("conceptAnswers", conceptAnswers);
	    	map.put("formsInUse", forms);
			
	    	//get complete class and datatype lists 
			map.put("classes", cs.getConceptClasses());
			map.put("datatypes", cs.getConceptDatatypes());
			
			// make spring locale available to jsp
			map.put("locale", locale.getLanguage());
			
		}
		
		return map;
	} 
}