package org.openmrs.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.Vector;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Concept;
import org.openmrs.ConceptNumeric;
import org.openmrs.api.ConceptService;
import org.openmrs.api.context.Context;

public class OpenmrsUtil {

	private static Log log = LogFactory.getLog(OpenmrsUtil.class);

	public static int getCheckDigit(String idWithoutCheckdigit)
			throws Exception {

		// allowable characters within identifier
		String validChars = "0123456789ABCDEFGHIJKLMNOPQRSTUVYWXZ_";

		// remove leading or trailing whitespace, convert to uppercase
		idWithoutCheckdigit = idWithoutCheckdigit.trim().toUpperCase();

		// this will be a running total
		int sum = 0;

		// loop through digits from right to left
		for (int i = 0; i < idWithoutCheckdigit.length(); i++) {

			// set ch to "current" character to be processed
			char ch = idWithoutCheckdigit.charAt(idWithoutCheckdigit.length()
					- i - 1);

			// throw exception for invalid characters
			if (validChars.indexOf(ch) == -1)
				throw new Exception("\"" + ch + "\" is an invalid character");

			// our "digit" is calculated using ASCII value - 48
			int digit = (int) ch - 48;

			// weight will be the current digit's contribution to
			// the running total
			int weight;
			if (i % 2 == 0) {

				// for alternating digits starting with the rightmost, we
				// use our formula this is the same as multiplying x 2 and
				// adding digits together for values 0 to 9. Using the
				// following formula allows us to gracefully calculate a
				// weight for non-numeric "digits" as well (from their
				// ASCII value - 48).
				weight = (2 * digit) - (int) (digit / 5) * 9;

			} else {

				// even-positioned digits just contribute their ascii
				// value minus 48
				weight = digit;

			}

			// keep a running total of weights
			sum += weight;

		}

		// avoid sum less than 10 (if characters below "0" allowed,
		// this could happen)
		sum = Math.abs(sum) + 10;

		// check digit is amount needed to reach next number
		// divisible by ten
		return (10 - (sum % 10)) % 10;

	}

	/**
	 * 
	 * @param id
	 * @return true/false whether id has a valid check digit
	 * @throws Exception
	 *             on invalid characters and invalid id formation
	 */
	public static boolean isValidCheckDigit(String id) throws Exception {

		if (!id.matches("^[A-Za-z0-9_]+-[0-9]$")) {
			throw new Exception("Invalid characters and/or id formation");
		}

		String idWithoutCheckDigit = id.substring(0, id.indexOf("-"));

		int computedCheckDigit = getCheckDigit(idWithoutCheckDigit);

		int givenCheckDigit = Integer.valueOf(id.substring(id.indexOf("-") + 1,
				id.length()));

		return (computedCheckDigit == givenCheckDigit);
	}

	/**
	 * Compares origList to newList returning map of differences
	 * 
	 * @param origList
	 * @param newList
	 * @return [List toAdd, List toDelete] with respect to origList
	 */
	public static <E extends Object> Collection<Collection<E>> compareLists(Collection<E> origList,
			Collection<E> newList) {
		// TODO finish function

		Collection<Collection<E>> returnList = new Vector<Collection<E>>();

		Collection<E> toAdd = new LinkedList<E>();
		Collection<E> toDel = new LinkedList<E>();

		// loop over the new list.
		for (E currentNewListObj : newList) {
			// loop over the original list
			boolean foundInList = false;
			for (E currentOrigListObj : origList) {
				// checking if the current new list object is in the original
				// list
				if (currentNewListObj.equals(currentOrigListObj)) {
					foundInList = true;
					origList.remove(currentOrigListObj);
					break;
				}
			}
			if (!foundInList)
				toAdd.add(currentNewListObj);

			// all found new objects were removed from the orig list,
			// leaving only objects needing to be removed
			toDel = origList;

		}

		returnList.add(toAdd);
		returnList.add(toDel);

		return returnList;
	}

	public static boolean isStringInArray(String str, String[] arr) {
		boolean retVal = false;

		if (str != null && arr != null) {
			for (int i = 0; i < arr.length; i++) {
				if (str.equals(arr[i]))
					retVal = true;
			}
		}
		return retVal;
	}

	public static Boolean isInNormalNumericRange(Float value,
			ConceptNumeric concept) {
		if (concept.getHiNormal() == null || concept.getLowNormal() == null)
			return false;
		return (value <= concept.getHiNormal() && value >= concept
				.getLowNormal());
	}

	public static Boolean isInCriticalNumericRange(Float value,
			ConceptNumeric concept) {
		if (concept.getHiCritical() == null || concept.getLowCritical() == null)
			return false;
		return (value <= concept.getHiCritical() && value >= concept
				.getLowCritical());
	}

	public static Boolean isInAbsoluteNumericRange(Float value,
			ConceptNumeric concept) {
		if (concept.getHiAbsolute() == null || concept.getLowAbsolute() == null)
			return false;
		return (value <= concept.getHiAbsolute() && value >= concept
				.getLowAbsolute());
	}

	public static Boolean isValidNumericValue(Float value,
			ConceptNumeric concept) {
		if (concept.getHiAbsolute() == null || concept.getLowAbsolute() == null)
			return true;
		return (value <= concept.getHiAbsolute() && value >= concept
				.getLowAbsolute());
	}

	public static String getFileAsString(File file) throws IOException {
		StringBuffer fileData = new StringBuffer(1000);
		BufferedReader reader = new BufferedReader(new FileReader(file));
		char[] buf = new char[1024];
		int numRead = 0;
		while ((numRead = reader.read(buf)) != -1) {
			String readData = String.valueOf(buf, 0, numRead);
			fileData.append(readData);
			buf = new char[1024];
		}
		reader.close();
		return fileData.toString();
	}

	/**
	 * Copy file from inputStream onto the outputStream
	 * 
	 * @param inputStream
	 * @param outputStream
	 * @throws IOException
	 */
	public static void copyFile(InputStream inputStream,
			OutputStream outputStream) throws IOException {
		byte[] c = new byte[1];
		while (inputStream.read(c) != -1)
			outputStream.write(c);
		outputStream.close();
	}
	
	/**
	 * Initialize global settings
	 * Find and load modules
	 * 
	 * @param p properties from runtime configuration
	 */
	public static void startup(Properties p) {
		
		// Override global OpenMRS constants if specified by the user

		// Allow for "demo" mode where patient data is obscured
		String val = p.getProperty("obscure_patients", null);
		if (val != null && "true".equalsIgnoreCase(val))
			OpenmrsConstants.OBSCURE_PATIENTS = true;

		val = p.getProperty("obscure_patients.family_name", null);
		if (val != null)
			OpenmrsConstants.OBSCURE_PATIENTS_FAMILY_NAME = val;
		
		val = p.getProperty("obscure_patients.given_name", null);
		if (val != null)
			OpenmrsConstants.OBSCURE_PATIENTS_GIVEN_NAME = val;
		
		val = p.getProperty("obscure_patients.middle_name", null);
		if (val != null)
			OpenmrsConstants.OBSCURE_PATIENTS_MIDDLE_NAME = val;

		// Override the default "openmrs" database name
		val = p.getProperty("connection.database_name", null);
		if (val == null) {
			// the database name wasn't supplied explicitly, guess it 
			//   from the connection string
			val = p.getProperty("connection.url", null);
		
			if (val != null) {
				try {
					int endIndex = val.lastIndexOf("?");
					if (endIndex == -1)
						endIndex = val.length();
					int startIndex = val.lastIndexOf("/", endIndex);
					val = val.substring(startIndex + 1, endIndex);
					OpenmrsConstants.DATABASE_NAME = val;
				}
				catch (Exception e) {
					log.fatal("Database name cannot be configured from 'connection.url' ." +
							"Either supply 'connection.database_name' or correct the url", e);
				}
			}
		}
		
		val = p.getProperty("connection.database_business_name", null);
		if (val == null)
			val = OpenmrsConstants.DATABASE_NAME;
		OpenmrsConstants.DATABASE_BUSINESS_NAME = val;
		
		//val = p.getProperty("module_repository_path", null);
		//if (val != null)
		//	OpenmrsConstants.MODULE_REPOSITORY_PATH = val;
		
		// Load OpenMRS Modules
		//ModuleUtil.loadModules();
		
		//for (Module mod : ModuleUtil.getModules()) {
		//	mod.startup(p);
		//}
	}
	
	
	/**
	 * Takes a String like "size=compact|order=date" and returns a Map<String,String> from the keys to the values.
	 * @param paramList
	 * @return
	 */
	public static Map<String, String> parseParameterList(String paramList) {
		Map<String, String> ret = new HashMap<String, String>();
		if (paramList != null && paramList.length() > 0) {
			String[] args = paramList.split("\\|");
			for (String s : args) {
				int ind = s.indexOf('=');
				if (ind <= 0) {
					throw new IllegalArgumentException("Misformed argument in dynamic page specification string: '" + s + "' is not 'key=value'.");
				}
				String name = s.substring(0, ind);
				String value = s.substring(ind + 1);
				ret.put(name, value);
			}
		}
		return ret;
	}
	
	public static <Arg1, Arg2 extends Arg1> boolean nullSafeEquals(Arg1 d1, Arg2 d2) {
		if (d1 == null)
			return d2 == null;
		else if (d2 == null)
			return false;
		else
			return d1.equals(d2);
	}

	/**
	 * Compares two java.util.Date objects, but handles java.sql.Timestamp (which is not directly comparable to a date)
	 * by dropping its nanosecond value.
	 */
	public static int compare(Date d1, Date d2) {
		if (d1 instanceof Timestamp && d2 instanceof Timestamp) {
			return d1.compareTo(d2);
		}
		if (d1 instanceof Timestamp)
			d1 = new Date(((Timestamp) d1).getTime());
		if (d2 instanceof Timestamp)
			d2 = new Date(((Timestamp) d2).getTime());
		return d1.compareTo(d2);
	}
	
	/**
	 * Compares two Date/Timestamp objects, treating null as the earliest possible date.
	 */
	public static int compareWithNullAsEarliest(Date d1, Date d2) {
		if (d1 == null)
			return -1;
		else if (d2 == null)
			return 1;
		else
			return compare(d1, d2);
	}
	
	/**
	 * Compares two Date/Timestamp objects, treating null as the earliest possible date.
	 */
	public static int compareWithNullAsLatest(Date d1, Date d2) {
		if (d1 == null)
			return 1;
		else if (d2 == null)
			return -1;
		else
			return compare(d1, d2);
	}
	
	public static <E extends Comparable<E>> int compareWithNullAsLowest(E c1, E c2) {
		if (c1 == null)
			return -1;
		else if (c2 == null)
			return 1;
		else
			return c1.compareTo(c2);
	}
	
	public static <E extends Comparable<E>> int comparewithNullAsGreatest(E c1, E c2) {
		if (c1 == null)
			return 1;
		else if (c2 == null)
			return -1;
		else
			return c1.compareTo(c2);
	}

	public static Integer ageFromBirthdate(Date birthdate) {
		if (birthdate == null)
			return null;
		
		Calendar today = Calendar.getInstance();
		
		Calendar bday = new GregorianCalendar();
		bday.setTime(birthdate);
		
		int age = today.get(Calendar.YEAR) - bday.get(Calendar.YEAR);
		
		//tricky bit:
		// set birthday calendar to this year
		// if the current date is less that the new 'birthday', subtract a year
		bday.set(Calendar.YEAR, today.get(Calendar.YEAR));
		if (today.before(bday)) {
				age = age -1;
		}

		return age;
	}
	
	/**
	 * Converts a collection to a String with a specified separator between all elements
	 * @param c Collection to be joined
	 * @param separator string to put between all elements
	 * @return a String representing the toString() of all elements in c, separated by separator
	 */
	public static <E extends Object> String join(Collection<E> c, String separator) {
		StringBuilder ret = new StringBuilder();
		for (Iterator i = c.iterator(); i.hasNext(); ) {
			ret.append(i.next());
			if (i.hasNext())
				ret.append(separator);
		}
		return ret.toString();
	}
	
	public static Set<Concept> conceptSetHelper(String descriptor) {
		Set<Concept> ret = new HashSet<Concept>();
		if (descriptor == null || descriptor.length() == 0)
			return ret;
		ConceptService cs = Context.getConceptService();
		
		for (StringTokenizer st = new StringTokenizer(descriptor, "|"); st.hasMoreTokens(); ) {
			String s = st.nextToken().trim();
			boolean isSet = s.startsWith("set:");
			if (isSet)
				s = s.substring(4).trim();
			Concept c = null;
			if (s.startsWith("name:")) {
				String name = s.substring(5).trim();
				c = cs.getConceptByName(name);
			} else {
				try {
					c = cs.getConcept(Integer.valueOf(s.trim()));
				} catch (Exception ex) { }
			}
			if (c != null) {
				if (isSet) {
					List<Concept> inSet = cs.getConceptsInSet(c);
					ret.addAll(inSet);
				} else {
					ret.add(c);
				}
			}
		}
		return ret;
	}

	public static List<Concept> delimitedStringToConceptList( String delimitedString, String delimiter, Context context ) {
		List<Concept> ret = null;
		
		if ( delimitedString != null && context != null ) {
			String[] tokens = delimitedString.split(delimiter);
			for ( String token : tokens ) {
				Integer conceptId = null;
				
				try {
					conceptId = new Integer(token);
				} catch (NumberFormatException nfe) {
					conceptId = null;
				}
				
				Concept c = null;
				
				if ( conceptId != null ) {
					c = Context.getConceptService().getConcept(conceptId);
				} else {
					c = Context.getConceptService().getConceptByName(token);
				}
				
				if ( c != null ) {
					if ( ret == null ) ret = new ArrayList<Concept>();
					ret.add(c);
				}
			}
		}
		
		return ret;
	}

	public static Map<String, Concept> delimitedStringToConceptMap( String delimitedString, String delimiter) {
		Map<String,Concept> ret = null;
		
		if ( delimitedString != null) {
			String[] tokens = delimitedString.split(delimiter);
			for ( String token : tokens ) {
				Concept c = OpenmrsUtil.getConceptByIdOrName(token);
				
				if ( c != null ) {
					if ( ret == null ) ret = new HashMap<String, Concept>();
					ret.put(token, c);
				}
			}
		}
		
		return ret;
	}

	public static Concept getConceptByIdOrName(String idOrName) {
		Concept c = null;
		Integer conceptId = null;
		
		try {
			conceptId = new Integer(idOrName);
		} catch (NumberFormatException nfe) {
			conceptId = null;
		}
		
		if ( conceptId != null ) {
			c = Context.getConceptService().getConcept(conceptId);
		} else {
			c = Context.getConceptService().getConceptByName(idOrName);
		}

		return c;
	}
	// TODO: properly handle duplicates
	public static List<Concept> conceptListHelper(String descriptor) {
		List<Concept> ret = new ArrayList<Concept>();
		if (descriptor == null || descriptor.length() == 0)
			return ret;
		ConceptService cs = Context.getConceptService();
		
		for (StringTokenizer st = new StringTokenizer(descriptor, "|"); st.hasMoreTokens(); ) {
			String s = st.nextToken().trim();
			boolean isSet = s.startsWith("set:");
			if (isSet)
				s = s.substring(4).trim();
			Concept c = null;
			if (s.startsWith("name:")) {
				String name = s.substring(5).trim();
				c = cs.getConceptByName(name);
			} else {
				try {
					c = cs.getConcept(Integer.valueOf(s.trim()));
				} catch (Exception ex) { }
			}
			if (c != null) {
				if (isSet) {
					List<Concept> inSet = cs.getConceptsInSet(c);
					ret.addAll(inSet);
				} else {
					ret.add(c);
				}
			}
		}
		return ret;
	}

}
