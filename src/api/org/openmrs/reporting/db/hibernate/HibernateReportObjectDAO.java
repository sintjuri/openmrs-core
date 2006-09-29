package org.openmrs.reporting.db.hibernate;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Query;
import org.hibernate.SessionFactory;
import org.openmrs.api.context.Context;
import org.openmrs.api.db.DAOException;
import org.openmrs.reporting.AbstractReportObject;
import org.openmrs.reporting.ReportObjectWrapper;
import org.openmrs.reporting.db.ReportObjectDAO;

public class HibernateReportObjectDAO implements
		ReportObjectDAO {

	protected final Log log = LogFactory.getLog(getClass());
	
	/**
	 * Hibernate session factory
	 */
	private SessionFactory sessionFactory;
	
	public HibernateReportObjectDAO() { }

	/**
	 * Set session factory
	 * 
	 * @param sessionFactory
	 */
	public void setSessionFactory(SessionFactory sessionFactory) { 
		this.sessionFactory = sessionFactory;
	}
	
	public Set<AbstractReportObject> getAllReportObjects() {
		Set<AbstractReportObject> reportObjects = new HashSet<AbstractReportObject>();
		Set<ReportObjectWrapper> wrappedObjects = new HashSet<ReportObjectWrapper>();
		wrappedObjects.addAll((ArrayList<ReportObjectWrapper>)sessionFactory.getCurrentSession().createQuery("from ReportObjectWrapper order by date_created, name").list());
		for ( ReportObjectWrapper wrappedObject : wrappedObjects ) {
			AbstractReportObject reportObject = (AbstractReportObject)wrappedObject.getReportObject();
			if ( reportObject.getReportObjectId() == null ) {
				reportObject.setReportObjectId(wrappedObject.getReportObjectId());
			}
			reportObjects.add(reportObject);
		}
		return reportObjects;
	}

	public AbstractReportObject getReportObject(Integer reportObjId) throws DAOException {
		ReportObjectWrapper wrappedReportObject = new ReportObjectWrapper();
		wrappedReportObject = (ReportObjectWrapper)sessionFactory.getCurrentSession().get(ReportObjectWrapper.class, reportObjId);
		
		AbstractReportObject reportObject = (wrappedReportObject == null) ? null : wrappedReportObject.getReportObject();
		if ( reportObject.getReportObjectId() == null ) reportObject.setReportObjectId(wrappedReportObject.getReportObjectId());
		
		return reportObject;
	}

	public void createReportObject(AbstractReportObject reportObj) throws DAOException {
		reportObj.setCreator(Context.getAuthenticatedUser());
		reportObj.setDateCreated(new Date());
		
		ReportObjectWrapper wrappedReportObject = new ReportObjectWrapper(reportObj);
		sessionFactory.getCurrentSession().save(wrappedReportObject);
	}

	public void deleteReportObject(AbstractReportObject reportObj) throws DAOException {
		ReportObjectWrapper wrappedReportObject = new ReportObjectWrapper(reportObj);		
		
		sessionFactory.getCurrentSession().delete(wrappedReportObject);
	}

	public void updateReportObject(AbstractReportObject reportObj) throws DAOException {
		if (reportObj.getCreator() == null)
			createReportObject(reportObj);
		else {
			ReportObjectWrapper wrappedReportObject = new ReportObjectWrapper(reportObj);		

			wrappedReportObject = (ReportObjectWrapper)sessionFactory.getCurrentSession().merge(wrappedReportObject);
			sessionFactory.getCurrentSession().update(wrappedReportObject);
		}
	}

	public Set<AbstractReportObject> getReportObjectsByType(String reportObjectType) throws DAOException {
		Set<AbstractReportObject> reportObjects = new HashSet<AbstractReportObject>();
		Set<ReportObjectWrapper> wrappedObjects = new HashSet<ReportObjectWrapper>();
		Query query = sessionFactory.getCurrentSession().createQuery("from ReportObjectWrapper ro where ro.type=:type order by date_created, name");
		query.setString("type", reportObjectType);
		wrappedObjects.addAll((ArrayList<ReportObjectWrapper>)query.list());
		for ( ReportObjectWrapper wrappedObject : wrappedObjects ) {
			AbstractReportObject reportObject = (AbstractReportObject)wrappedObject.getReportObject();
			if ( reportObject.getReportObjectId() == null ) {
				reportObject.setReportObjectId(wrappedObject.getReportObjectId());
			}
			reportObjects.add(reportObject);
		}
		return reportObjects;
	}
}
