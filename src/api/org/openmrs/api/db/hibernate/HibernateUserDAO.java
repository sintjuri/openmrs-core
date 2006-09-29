package org.openmrs.api.db.hibernate;

import java.util.Date;
import java.util.List;
import java.util.Vector;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Criteria;
import org.hibernate.Hibernate;
import org.hibernate.NonUniqueObjectException;
import org.hibernate.Query;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Expression;
import org.hibernate.criterion.MatchMode;
import org.hibernate.criterion.Order;
import org.openmrs.Person;
import org.openmrs.Privilege;
import org.openmrs.Role;
import org.openmrs.User;
import org.openmrs.api.APIException;
import org.openmrs.api.context.Context;
import org.openmrs.api.db.DAOException;
import org.openmrs.api.db.UserDAO;
import org.openmrs.util.OpenmrsUtil;
import org.openmrs.util.Security;
import org.springframework.orm.ObjectRetrievalFailureException;

public class HibernateUserDAO implements
		UserDAO {

	protected final Log log = LogFactory.getLog(getClass());
	
	/**
	 * Hibernate session factory
	 */
	private SessionFactory sessionFactory;
	
	public HibernateUserDAO() { }

	/**
	 * Set session factory
	 * 
	 * @param sessionFactory
	 */
	public void setSessionFactory(SessionFactory sessionFactory) { 
		this.sessionFactory = sessionFactory;
	}
	
	/**
	 * @see org.openmrs.api.db.UserService#createUser(org.openmrs.User)
	 */
	public void createUser(User user, String password) {
		if (hasDuplicateUsername(user))
			throw new DAOException("Username currently in use by '" + user.getFirstName() + " " + user.getLastName() + "'");
		
			
		String systemId = generateSystemId();
		Integer checkDigit;
		try {
			checkDigit = OpenmrsUtil.getCheckDigit(systemId);
		}
		catch ( Exception e ) {
			log.error("error getting check digit", e);
			return;
		}
		user.setSystemId(systemId + "-" + checkDigit);
		
		user = updateProperties(user);
		sessionFactory.getCurrentSession().save(user);

		// create a Person for this user as well.
		Person person = new Person();
		person.setUser(user);
		sessionFactory.getCurrentSession().save(person);
		
		//Context.closeSession();
		
		//update the new user with the password
		//Context.openSession();
		String salt = Security.getRandomToken();
		String hashedPassword = Security.encodeString(password + salt);
		sessionFactory.getCurrentSession().createQuery("update User set password = :pw, salt = :salt where user_id = :username")
			.setParameter("pw", hashedPassword)
			.setParameter("salt", salt)
			.setParameter("username", user.getUserId())
			.executeUpdate();
			
	}

	/**
	 * @see org.openmrs.api.db.UserService#getUserByUsername(java.lang.String)
	 */
	@SuppressWarnings("unchecked")
	public User getUserByUsername(String username) {
		List<User> users = sessionFactory.getCurrentSession()
				.createQuery(
						"from User u where u.voided = 0 and (u.username = ? or u.systemId = ?)")
				.setString(0, username)
				.setString(1, username)
				.list();
		
		if (users == null || users.size() == 0) {
			log.warn("request for username '" + username + "' not found");
			return null;
			//throw new ObjectRetrievalFailureException(User.class, username);
		}

		return users.get(0);
	}

	/**
	 * @see org.openmrs.api.db.UserService#hasDuplicateUsername(org.openmrs.User)
	 */
	public boolean hasDuplicateUsername(User user) {
		String username = user.getUsername();
		if (username == null || username.length() == 0)
			username = "-";
		String systemId = user.getSystemId();
		if (systemId == null || username.length() == 0)
			systemId = "-";
		
		Integer userid = user.getUserId();
		if (userid == null)
			userid = new Integer(-1);
		
		String usernameWithCheckDigit = username;
		try {
			Integer cd = OpenmrsUtil.getCheckDigit(username);
			usernameWithCheckDigit = usernameWithCheckDigit + "-" + cd;
		}
		catch (Exception e) {}
		
		Integer count = (Integer) sessionFactory.getCurrentSession().createQuery(
				"select count(*) from User u where (u.username = :uname1 or u.systemId = :uname2 or u.username = :sysid1 or u.systemId = :sysid2 or u.systemId = :uname3) and u.userId <> :uid")
				.setString("uname1", username)
				.setString("uname2", username)
				.setString("sysid1", systemId)
				.setString("sysid2", systemId)
				.setString("uname3", usernameWithCheckDigit)
				.setInteger("uid", userid)
				.uniqueResult();

		log.debug("# users found: " + count);
		if (count == null || count == 0)
			return false;
		else
			return true;
	}
	
	/**
	 * @see org.openmrs.api.db.UserService#getUser(java.lang.Long)
	 */
	public User getUser(Integer userId) {
		User user = (User) sessionFactory.getCurrentSession().get(User.class, userId);
		
		if (user == null) {
			log.warn("request or user '" + userId + "' not found");
			throw new ObjectRetrievalFailureException(User.class, userId);
		}
		return user;
	}
	
	/**
	 * @see org.openmrs.api.db.UserService#getUsers()
	 */
	@SuppressWarnings("unchecked")
	public List<User> getUsers() throws DAOException {
		return sessionFactory.getCurrentSession().createQuery("from User u order by u.userId")
								.list();
	}

	/**
	 * @see org.openmrs.api.db.UserService#updateUser(org.openmrs.User)
	 */
	public void updateUser(User user) {
		if (user.getCreator() == null)
			createUser(user, "");
		else {
			if (log.isDebugEnabled()) {
				log.debug("update user id: " + user.getUserId());
			}
			user = updateProperties(user);
			try {
				sessionFactory.getCurrentSession().update(user);
			}
			catch (NonUniqueObjectException e) {
				sessionFactory.getCurrentSession().merge(user);
			}
		}
	}

	/**
	 * @see org.openmrs.api.db.UserService#deleteUser(org.openmrs.User)
	 */
	public void deleteUser(User user) {
		sessionFactory.getCurrentSession().delete(user);
	}

	/**
	 * @see org.openmrs.api.db.UserService#getUserByRole(org.openmrs.Role)
	 */
	@SuppressWarnings("unchecked")
	public List<User> getUsersByRole(Role role) throws DAOException {
		List<User> users = sessionFactory.getCurrentSession().createCriteria(User.class, "u")
						.createCriteria("roles", "r")
						.add(Expression.like("r.role", role.getRole()))
						.addOrder(Order.asc("u.username"))
						.list();
		
		return users;
		
	}

	/**
	 * @see org.openmrs.api.db.UserService#getPrivileges()
	 */
	@SuppressWarnings("unchecked")
	public List<Privilege> getPrivileges() throws DAOException {
		return sessionFactory.getCurrentSession().createQuery("from Privilege p order by p.privilege").list();
	}

	/**
	 * @see org.openmrs.api.db.UserService#getRoles()
	 */
	@SuppressWarnings("unchecked")
	public List<Role> getRoles() throws DAOException {
		return sessionFactory.getCurrentSession().createQuery("from Role r order by r.role").list();
	}
	
	/**
	 * @see org.openmrs.api.db.UserService#getInheritingRoles(org.openmrs.Role)
	 */
	@SuppressWarnings("unchecked")
	public List<Role> getInheritingRoles(Role role) throws DAOException {
		/*
		Criteria crit = session.createCriteria(Role.class, "r")
			.add(Expression.in("r.inheritedRoles", role))
			.addOrder(Order.asc("r.role"));
		return crit.list();
		*/
		
		return sessionFactory.getCurrentSession().createQuery("from Role r where :role in inheritedRoles order by r.role")
				.setParameter("role", role)
				.list();
	}

	/**
	 * @see org.openmrs.api.db.UserService#getPrivilege()
	 */
	public Privilege getPrivilege(String p) throws DAOException {
		return (Privilege)sessionFactory.getCurrentSession().get(Privilege.class, p);
	}

	/**
	 * @see org.openmrs.api.db.UserService#getRole()
	 */
	public Role getRole(String r) throws DAOException {
		return (Role)sessionFactory.getCurrentSession().get(Role.class, r);
	}
	
	/**
	 * @see org.openmrs.api.db.UserDAO#changePassword(org.openmrs.User, java.lang.String)
	 */
	public void changePassword(User u, String pw) throws DAOException {
		User authUser = Context.getAuthenticatedUser();
		
		if (authUser == null)
			authUser = u;
		
		log.debug("udpating password");
		//update the user with the new password
		String salt = Security.getRandomToken();
		String newPassword = Security.encodeString(pw + salt);
		sessionFactory.getCurrentSession().createQuery("update User set password = :pw, salt = :salt, changed_by = :changed, date_changed = :date where user_id = :userid")
			.setParameter("pw", newPassword)
			.setParameter("salt", salt)
			.setParameter("userid", u.getUserId())
			.setParameter("changed", authUser.getUserId())
			.setParameter("date", new Date())
			.executeUpdate();
	}

	/**
	 * @see org.openmrs.api.db.UserDAO#changePassword(java.lang.String, java.lang.String)
	 */
	public void changePassword(String pw, String pw2) throws DAOException {
		User u = Context.getAuthenticatedUser();
		
		String passwordOnRecord = (String) sessionFactory.getCurrentSession().createSQLQuery(
			"select password from users where user_id = ?")
			.addScalar("password", Hibernate.STRING)
			.setInteger(0, u.getUserId())
			.uniqueResult();
		
		String saltOnRecord = (String) sessionFactory.getCurrentSession().createSQLQuery(
			"select salt from users where user_id = ?")
			.addScalar("salt", Hibernate.STRING)
			.setInteger(0, u.getUserId())
			.uniqueResult();

		String hashedPassword = Security.encodeString(pw + saltOnRecord);
		
		if (!passwordOnRecord.equals(hashedPassword)) {
			log.error("Passwords don't match");
			throw new DAOException("Passwords don't match");
		}
		
		log.debug("udpating password");
		//update the user with the new password
		String salt = Security.getRandomToken();
		String newPassword = Security.encodeString(pw2 + salt);
		sessionFactory.getCurrentSession().createQuery("update User set password = :pw, salt = :salt where user_id = :userid")
			.setParameter("pw", newPassword)
			.setParameter("salt", salt)
			.setParameter("userid", u.getUserId())
			.executeUpdate();
	}
	
	public void changeQuestionAnswer(String pw, String question, String answer) throws DAOException {
		User u = Context.getAuthenticatedUser();
		
		String passwordOnRecord = (String) sessionFactory.getCurrentSession().createSQLQuery(
		"select password from users where user_id = ?")
		.addScalar("password", Hibernate.STRING)
		.setInteger(0, u.getUserId())
		.uniqueResult();
		
		String saltOnRecord = (String) sessionFactory.getCurrentSession().createSQLQuery(
		"select salt from users where user_id = ?")
		.addScalar("salt", Hibernate.STRING)
		.setInteger(0, u.getUserId())
		.uniqueResult();
		
		try {
			String hashedPassword = Security.encodeString(pw + saltOnRecord);
			
			if (!passwordOnRecord.equals(hashedPassword)) {
				throw new DAOException("Passwords don't match");
			}
		}
		catch (APIException e) {
			log.error(e);
			throw new DAOException(e);
		}
		
		sessionFactory.getCurrentSession().createQuery("update User set secret_question = :q, secret_answer = :a where user_id = :userid")
			.setParameter("q", question)
			.setParameter("a", answer)
			.setParameter("userid", u.getUserId())
			.executeUpdate();
	}
	
	public boolean isSecretAnswer(User u, String answer) throws DAOException {
		
		if (answer == null || answer.equals(""))
			return false;
		
		String answerOnRecord = "";
		
		try {
			answerOnRecord = (String) sessionFactory.getCurrentSession().createSQLQuery(
			"select secret_answer from users where user_id = ?")
			.addScalar("secret_answer", Hibernate.STRING)
			.setInteger(0, u.getUserId())
			.uniqueResult();
		}
		catch (Exception e) {
			return false;
		}
		
		return (answerOnRecord.equals(answer));
		
		
	}
	
	@SuppressWarnings("unchecked")
	public List<User> findUsers(String name, List<String> roles, boolean includeVoided) {
		name = name.replace(", ", " ");
		String[] names = name.split(" ");
		
		log.debug("name: " + name);
		
		Criteria criteria = sessionFactory.getCurrentSession().createCriteria(User.class);
		//criteria.setProjection(Projections.distinct(Projections.property("user")));
		for (String n : names) {
			if (n != null && n.length() > 0) {
				criteria.add(Expression.or(
						Expression.like("lastName", n, MatchMode.START),
						Expression.or(
							Expression.like("firstName", n, MatchMode.START),
								Expression.or(
										Expression.like("middleName", n, MatchMode.START),
										Expression.like("systemId", n, MatchMode.START)
										)
							)
						)
					);
			}
		}
		
		/*
		if (roles != null && roles.size() > 0) {
			criteria.createAlias("roles", "r")
				.add(Expression.in("r.role", roles))
				.createAlias("groups", "g")
				.createAlias("g.roles", "gr")
					.add(Expression.or(
							Expression.in("r.role", roles),
							Expression.in("gr.role", roles)
							));
		}
		 */
		
		if (includeVoided == false)
			criteria.add(Expression.eq("voided", false));

		// TODO figure out how to get Hibernate to do the sql for us
		
		List returnList = new Vector();
		if (roles != null && roles.size() > 0) {
			for (Object o : criteria.list()) {
				User u = (User)o;
				for (String r : roles)
					if (u.hasRole(r, true)) {
						returnList.add(u);
						break;
					}
			}
		}
		else
			returnList = criteria.list();
		
		return returnList;
	}
	
	@SuppressWarnings("unchecked")
	public List<User> getAllUsers(List<Role> roles, boolean includeVoided) {
		Criteria criteria = sessionFactory.getCurrentSession().createCriteria(User.class);
		
		if (includeVoided == false)
			criteria.add(Expression.eq("voided", false));

		List<User> returnList = new Vector<User>();
		if (roles != null && roles.size() > 0) {
			for (Object o : criteria.list()) {
				User u = (User)o;
				for (Role r : roles)
					if (r != null) {
						if (u.hasRole(r.getRole(), true)) {
							returnList.add(u);
							break;
						}
					}
			}
		}
		else
			returnList = criteria.list();
		
		return returnList;
		
		// TODO figure out how to get Hibernate to do the sql for us
	}
	
	/**
	 * Get/generate/find the next system id to be doled out.  Assume check digit /not/ applied
	 * in this method
	 * @return new system id
	 */
	public String generateSystemId() {
		String sql = "select max(user_id) as user_id from users";
		
		Query query = sessionFactory.getCurrentSession().createSQLQuery(sql);
		
		Integer id = ((Integer)query.uniqueResult()).intValue() + 1;
		
		return id.toString();
	}

	private User updateProperties(User user) {
		
		if (user.getCreator() == null) {
			user.setDateCreated(new Date());
			user.setCreator(Context.getAuthenticatedUser());
		}
		
		user.setChangedBy(Context.getAuthenticatedUser());
		user.setDateChanged(new Date());
		
		return user;
		
	}

	@SuppressWarnings("unchecked")
	public List<User> findUsers(String firstName, String lastName, boolean includeVoided) {
		List<User> users = new Vector<User>();
		String query = "from User u where u.firstName = :firstName and u.lastName = :lastName";
		if (!includeVoided)
			query += " and u.voided = false";
		Query q = sessionFactory.getCurrentSession().createQuery(query)
				.setString("firstName", firstName)
				.setString("lastName", lastName);
		for (User u : (List<User>) q.list()) {
			users.add(u);
		}
		return users;
	}
}
