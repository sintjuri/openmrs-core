package org.openmrs.web.dwr;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Vector;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.PatientProgram;
import org.openmrs.PatientState;
import org.openmrs.ProgramWorkflow;
import org.openmrs.ProgramWorkflowState;
import org.openmrs.api.ProgramWorkflowService;
import org.openmrs.api.context.Context;
import org.openmrs.util.OpenmrsUtil;

public class DWRProgramWorkflowService {

	protected final Log log = LogFactory.getLog(getClass());
	
	public PatientProgramItem getPatientProgram(Integer patientProgramId) {
		return new PatientProgramItem(Context.getProgramWorkflowService().getPatientProgram(patientProgramId));
	}
	
	public Vector<PatientStateItem> getPatientStates(Integer patientProgramId, Integer programWorkflowId) {
		Vector<PatientStateItem> ret = new Vector<PatientStateItem>();
		ProgramWorkflowService s = Context.getProgramWorkflowService();
		PatientProgram p = s.getPatientProgram(patientProgramId);
		ProgramWorkflow wf = s.getWorkflow(programWorkflowId);
		for (PatientState st : p.statesInWorkflow(wf, false))
			ret.add(new PatientStateItem(st));
		return ret;
	}

	
	DateFormat ymdDf = new SimpleDateFormat("yyyy-MM-dd");
	public void updatePatientProgram(Integer patientProgramId, String enrollmentDateYmd, String completionDateYmd) throws ParseException {
		PatientProgram pp = Context.getProgramWorkflowService().getPatientProgram(patientProgramId);
		Date dateEnrolled = null;
		Date dateCompleted = null;
		if (enrollmentDateYmd != null && enrollmentDateYmd.length() > 0)
			dateEnrolled = ymdDf.parse(enrollmentDateYmd);
		if (completionDateYmd != null && completionDateYmd.length() > 0)
			dateCompleted = ymdDf.parse(completionDateYmd);
		boolean anyChange = OpenmrsUtil.nullSafeEquals(dateEnrolled, pp.getDateEnrolled());
		anyChange |= OpenmrsUtil.nullSafeEquals(dateCompleted, pp.getDateCompleted());
		if (anyChange) {
			pp.setDateEnrolled(dateEnrolled);
			pp.setDateCompleted(dateCompleted);
			Context.getProgramWorkflowService().updatePatientProgram(pp);
		}
	}
	
	public void deletePatientProgram(Integer patientProgramId, String reason) {
		PatientProgram p = Context.getProgramWorkflowService().getPatientProgram(patientProgramId);
		Context.getProgramWorkflowService().voidPatientProgram(p, reason);
	}
	
	public Vector<ListItem> getPossibleNextStates(Integer patientProgramId, Integer programWorkflowId) {
		Vector<ListItem> ret = new Vector<ListItem>();
		List<ProgramWorkflowState> states = Context.getProgramWorkflowService()
			.getPossibleNextStates(Context.getProgramWorkflowService().getPatientProgram(patientProgramId),
								   Context.getProgramWorkflowService().getWorkflow(programWorkflowId));
		for (ProgramWorkflowState state : states) {
			ListItem li = new ListItem();
			li.setId(state.getProgramWorkflowStateId());
			li.setName(state.getConcept().getName(Context.getLocale(), false).getName());
			ret.add(li);
		}
		return ret;
	}
	
	public void changeToState(Integer patientProgramId, Integer programWorkflowId,
		Integer programWorkflowStateId, String onDateDMY) throws ParseException {
		ProgramWorkflowService s = Context.getProgramWorkflowService(); 
		PatientProgram pp = s.getPatientProgram(patientProgramId);
		ProgramWorkflow wf = s.getWorkflow(programWorkflowId);
		ProgramWorkflowState st = s.getState(programWorkflowStateId);
		Date onDate = null;
		if (onDateDMY != null && onDateDMY.length() > 0)
			onDate = ymdDf.parse(onDateDMY);
		s.changeToState(pp, wf, st, onDate);
	}
	
	public void voidLastState(Integer patientProgramId, Integer programWorkflowId, String voidReason) {
		ProgramWorkflowService s = Context.getProgramWorkflowService();
		PatientProgram pp = s.getPatientProgram(patientProgramId);
		ProgramWorkflow wf = s.getWorkflow(programWorkflowId);
		s.voidLastState(pp, wf, voidReason);
	}
}
