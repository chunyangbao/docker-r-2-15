/*
 The Broad Institute
 SOFTWARE COPYRIGHT NOTICE AGREEMENT
 This software and its documentation are copyright (2003-2008) by the
 Broad Institute/Massachusetts Institute of Technology. All rights are
 reserved.
 
 This software is supplied without any warranty or guaranteed support
 whatsoever. Neither the Broad Institute nor MIT can be responsible for its
 use, misuse, or functionality.
 */

package org.genepattern.server.webapp.jsf;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import javax.faces.application.FacesMessage;
import javax.faces.component.UIComponent;
import javax.faces.component.UIInput;
import javax.faces.context.FacesContext;
import javax.faces.model.SelectItem;
import javax.faces.validator.ValidatorException;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.log4j.Logger;
import org.genepattern.server.UserAccountManager;
import org.genepattern.server.webapp.LoginManager;
import org.genepattern.server.webservice.server.dao.BaseDAO;
import org.genepattern.util.GPConstants;

public class RegisterServerBean {
    private static Logger log = Logger.getLogger(RegisterServerBean.class);

    //see ModuleRepository.SimpleAuthenticator
    private static class SimpleAuthenticator extends Authenticator {
        private String username, password;

        public SimpleAuthenticator(String username, String password) {
            this.username = username;
            this.password = password;
        }

        protected PasswordAuthentication getPasswordAuthentication() {
            return new PasswordAuthentication(username, password.toCharArray());
        }
    }
	  
    // not in properties file so it cannot be (easily) overridden, but can be configured via setRegistrationUrl
    private String registrationUrl="http://www.broad.mit.edu/cgi-bin/cancer/software/genepattern/gp_server_license_process.cgi";  
    private String email;
    private String name;
    private String title;
    private String organization;
    private String department;
    private String address1;
    private String address2;
    private String city;
    private String state;
    private String zipCode;
    private String country;
    private boolean acceptLicense = false;
    private String os;
    private boolean error = false;
	  
    private boolean joinMailingList = true;
    private String[] countries = { "United States of America", "Albania",
			"Algeria", "American Samoa", "Andorra", "Angola", "Anguila",
			"Antarctica", "Antigua and Barbuda", "Argentina", "Armenia",
			"Aruba", "Australia", "Austria", "Azerjaijan", "Bahamas",
			"Bahrain", "Bangladesh", "Barbados", "Belarus", "Belgium",
			"Belize", "Benin", "Bermuda", "Bhutan", "Bolivia",
			"Bosnia and Herzegovina", "Botswana", "Bouvet Island", "Brazil",
			"British Indian Ocean territory", "Brunei Darussalam", "Bulgaria",
			"Burkina Faso", "Burundi", "Cambodia", "Cameroon", "Canada",
			"Cape Verde", "Cayman Islands", "Central African Republic", "Chad",
			"Chile", "China", "Christmas Island", "Cocos (Keeling) Islands",
			"Colombia", "Comoros", "Congo", "Cook Islands", "Costa Rica",
			"Croatia (Hrvatska)", "Cuba", "Cyprus", "Czech Republic",
			"Denmark", "Djibouti", "Dominica", "Dominican Republic",
			"East Timor", "Ecuador", "Egypt", "El Salvador",
			"Equatorial Guinea", "Eritrea", "Estonia", "Ethiopia",
			"Falkland Islands", "Faroe Islands", "Fiji", "Finland", "France",
			"French Guiana", "French Polynesia", "French Southern Territories",
			"Gabon", "Gambia", "Georgia", "Germany", "Ghana", "Greece",
			"Greenland", "Grenada", "Guadaloupe", "Guam", "Guatamala",
			"Guinea-Bissau", "Guinea", "Guyana", "Haiti",
			"Heard and McDonald Islands", "Honduras", "Hong Kong", "Hungary",
			"Iceland", "India", "Indonesia", "Iran", "Iraq", "Ireland",
			"Israel", "Italy", "Ivory Coast", "Jamaica", "Japan", "Jordan",
			"Kazakhstan", "Kenya", "Kiribati", "Korea (North)",
			"Korea (South)", "Kuwait", "Kyrgyzstan", "Laos", "Latvia",
			"Lebanon", "Lesotho", "Liberia", "Liechtenstein", "Lithuania",
			"Luxembourg", "Macau", "Macedonia", "Madagascar", "Malasia",
			"Malawi", "Maldives", "Mali", "Malta", "Marshal Islands",
			"Martinique", "Mauritania", "Maurritius", "Mayotte", "Mexico",
			"Micronesia", "Moldova", "Monaco", "Mongolia", "Montserrat",
			"Morocco", "Mozambique", "Mynamar", "Namibia", "Nauru", "Nepal",
			"Netherland Antilles", "Netherlands", "New Caledonia",
			"New Zealand", "Nicaragua", "Niger", "Nigeria", "Niue",
			"Norfolk Island", "Northern Marianas Islands", "Norway", "Oman",
			"Pakistan", "Palau", "Panama", "Papua New Guinea", "Paraguay",
			"Peru", "Philippines", "Pitcairn", "Poland", "Portugal",
			"Puerto Rico", "Qatar", "Reunion", "Romania", "Russian Federation",
			"Rwanda", "Saint Helena", "Saint Kitts and Nevis", "Saint Lucia",
			"Saint Pierre and Miquelon", "Saint Vincent and the Grenadines",
			"Samoa", "San Marino", "Sao Tome and Principe", "Saudi Arabia",
			"Senegal", "Seychelles", "Sierra Leone", "Singapore",
			"Slovak Republic", "Slovenia", "Solomon Islands", "Somalia",
			"South Africa", "South Georgia/South Sandwich Islands", "Spain",
			"Sri Lanka", "Sudan", "Suriname", "Svalbard and Jan Mayen Islands",
			"Swaziland", "Sweden", "Switzerland", "Syria", "Taiwan",
			"Tajikistan", "Tanzania", "Thailand", "Togo", "Tokelau", "Tonga",
			"Trinidad and Tobego", "Tunisia", "Turkey", "Turkmenistan",
			"Turks and Caicos Islands", "Tuvalu", "Uganda", "Ukraine",
			"United Arab Emirates", "United Kingdom", "Uruguay", "Uzbekistan",
			"Vanuatu", "Vatican City", "Venezuela", "Vietnam",
			"Virgin Islands (British)", "Virgin Islands (US)",
			"Wallis and Futuna Islands", "Western Sahara", "Yemen",
			"Yugoslavia", "Zaire", "Zambia", "Zimbabwe" };  
	    
    public RegisterServerBean() {
        this.email = System.getProperty("webmaster","");
    }

    private String handleException(Exception e) {
        System.setProperty(GPConstants.REGISTERED_SERVER, "unregistered");
        e.printStackTrace();
        error = true;
        return "unregisteredServer";
    }

    private String encodeParameter(String key, String value) throws UnsupportedEncodingException {
        return URLEncoder.encode(key, "UTF-8") + "=" + URLEncoder.encode(value, "UTF-8");
    }

    public String  registerServer() {
        String os = System.getProperty("os.name") + ", "+ System.getProperty("os.version");
        String genepatternVersion = System.getProperty("GenePatternVersion");
        String buildTag = System.getProperty("build.tag");
        URLConnection conn = null;
        URL url = null;
        try {
            url = new URL(registrationUrl);

            //set proxy crededentials if necessary
            String user = System.getProperty("http.proxyUser");
            String pass = System.getProperty("http.proxyPassword");
            if ((user != null) && (pass != null)) {
                Authenticator.setDefault(new SimpleAuthenticator(user, pass));
            }

            conn = url.openConnection();
            ((HttpURLConnection)conn).setRequestMethod("POST");
            conn.setDoOutput(true);

            String data = encodeParameter("component","Server");
            data += "&" + encodeParameter("gpversion",genepatternVersion);
            data += "&" + encodeParameter("build",buildTag);
            data += "&" + encodeParameter("os", os);
             
            data += "&" + encodeParameter("name",this.name);
            if (title != null) {
                data += "&" + encodeParameter("title",this.title);
            }
            data += "&" + encodeParameter("email",this.email);
            data += "&" + encodeParameter("organization",this.organization);
            data += "&" + encodeParameter("department",this.department);
            data += "&" + encodeParameter("address1",this.address1);
            if (address2 != null) {
                data += "&" + encodeParameter("address2",this.address2);
            }
            data += "&" + encodeParameter("city",this.city);
            data += "&" + encodeParameter("state",this.state);
            if (zipCode != null) {
                data += "&" + encodeParameter("zip",this.zipCode);
            }
            data += "&" + encodeParameter("country",this.country);
            data += "&" + encodeParameter("join", ""+this.joinMailingList);
            data += "&" + encodeParameter("os", os);
            
            OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
            wr.write(data);
            wr.flush();
            wr.close();
           
            int responseCode = ((HttpURLConnection)conn).getResponseCode();
            if (responseCode < 200 || responseCode >= 400) {
                throw new HttpException();
            }

            saveIsRegistered();
            UserAccountManager.instance().createUser(email);
            LoginManager.instance().addUserIdToSession(UIBeanHelper.getRequest(), email);
            error = false;              
            return "installFrame";
        }
        catch (MalformedURLException e) {
            return handleException(e);
        }
        catch (IOException e) {
            return handleException(e);
        }
        catch (Exception e) {
            return handleException(e);
        }
    }

    public String cancelRegistration() {
		   System.setProperty(GPConstants.REGISTERED_SERVER, "unregistered");
		   String os = System.getProperty("os.name") + ", "+ System.getProperty("os.version");
		   String genepatternVersion = System.getProperty("GenePatternVersion");
	        
		   HttpClient client = new HttpClient();
		   PostMethod httppost = new PostMethod(registrationUrl);
		  
		   httppost.addParameter("name","Anonymous");
		   httppost.addParameter("component","Server");
		   httppost.addParameter("gpversion",genepatternVersion);
		   httppost.addParameter("build",System.getProperty("build.tag"));
		   httppost.addParameter("os", os);
			   
		   httppost.addParameter("email","");
		   httppost.addParameter("component","Server");
		   httppost.addParameter("organization","");
		   httppost.addParameter("department","");
		   httppost.addParameter("address1","");
		   httppost.addParameter("city","");
		   httppost.addParameter("state","");
		   httppost.addParameter("country","");
		   httppost.addParameter("join", "false");
		   
		   // let them go on in if there was an exception but don't save 
		   // the registration to the DB.  They will be asked to register again
		   // after each restart
		   try {
		       UserAccountManager.instance().createUser(email);
		       LoginManager.instance().addUserIdToSession(UIBeanHelper.getRequest(), email);
			   
			   int responseCode = client.executeMethod(httppost);
			   if (responseCode < 200 || responseCode >= 400) throw new HttpException();
			   // we don't know them, but their download is recorded so mark the server as registered
			   saveIsRegistered();
		   } 
		   catch (Exception e) {
			   // swallow it and return
			   // didn't get a record back at the mother ship from the post so
			   // make the registration only good until restart
		   }
		   return "unregisteredServer";
    }

    public static boolean isRegisteredOrDeclined(){	
        if (System.getProperty(GPConstants.REGISTERED_SERVER, null) != null) return true;    
        else return isRegistered();
    }

    public static boolean isRegistered() {
        log.debug("checking registration");
        final String genepatternVersion = System.getProperty("GenePatternVersion");
        String dbRegisteredVersion = getDbRegisteredVersion();
        if (dbRegisteredVersion == null || dbRegisteredVersion.equals("")) {
            return false;
        }
        return (genepatternVersion.compareTo(dbRegisteredVersion) <= 0);
    }

    /**
     * @return true if this is an update from a previously registered version of GenePattern, <code>e.g. from 3.1 to 3.1.1</code>.
     */
    public boolean getIsUpdate() {
        List<String> dbEntries = getDbRegisteredVersions();
        final String genepatternVersion = System.getProperty("GenePatternVersion");
        if (dbEntries.contains("registeredVersion"+genepatternVersion)) {
            //already registered
            return false;
        }
        if (dbEntries.size() > 0) {
            return true;
        }
        return false;
    }

    /**
     * Lookup the registration key from the database, return an empty string
     * if there is no entry in the database.
     * @return
     */
    private static String getDbRegisteredVersion() {
        log.debug("getting registration info from database");
        String dbRegisteredVersion = "";
        final String genepatternVersion = System.getProperty("GenePatternVersion");
        final String sql = "select value from props where key='registeredVersion"+genepatternVersion+"'";
        try {
            BaseDAO dao = new BaseDAO();
            ResultSet resultSet = dao.executeSQL(sql, false);
            if (resultSet.next()) {
                dbRegisteredVersion = resultSet.getString(1);
            }
        } 
        catch (Exception e) {
            log.error("Didn't get registration info from database: "+e.getLocalizedMessage(), e);
        }
        return dbRegisteredVersion;
    }

    private static List<String> getDbRegisteredVersions() {
        log.debug("getting registration info from database");
        List<String> dbEntries = new ArrayList<String>();
        final String sql = "select VALUE from PROPS where key like 'registeredVersion%' order by key";
        try {
            BaseDAO dao = new BaseDAO();
            ResultSet resultSet = dao.executeSQL(sql, false);
            while(resultSet.next()) {
                dbEntries.add(resultSet.getString(1));
            }
        } 
        catch (Exception e) {
            log.error("Didn't get registration info from database: "+e.getLocalizedMessage(), e);
        }
        return dbEntries;
    }

    private static void saveIsRegistered() {
        log.debug("saving registration");
        String genepatternVersion = System.getProperty("GenePatternVersion");
        String dbRegisteredVersion = System.getProperty(GPConstants.REGISTERED_SERVER, null);
        if (dbRegisteredVersion != null) {
            return;
        }

        // update the DB
        try {
            BaseDAO dao = new BaseDAO();
            String sqlIns = "insert into props values ('registeredVersion"+genepatternVersion+"', '"+genepatternVersion+"')";
            dao.executeUpdate(sqlIns);
            System.setProperty(GPConstants.REGISTERED_SERVER, genepatternVersion);
        } 
        catch (Exception e) {
            // either DB is down or we already have it there
            log.debug(e);
        }
    }

    public void setRegistrationUrl(String url) {
        this.registrationUrl = url;
    }
	   
	public String getEmail() {
		return email;
	}


	public void setEmail(String email) {
		this.email = email;
	}


	public String getName() {
		return name;
	}


	public void setName(String name) {
		this.name = name;
	}


	public String getTitle() {
		return title;
	}


	public void setTitle(String title) {
		this.title = title;
	}


	public String getOrganization() {
		return organization;
	}


	public void setOrganization(String organization) {
		this.organization = organization;
	}


	public String getDepartment() {
		return department;
	}


	public void setDepartment(String department) {
		this.department = department;
	}


	public String getAddress1() {
		return address1;
	}


	public void setAddress1(String address1) {
		this.address1 = address1;
	}


	public String getAddress2() {
		return address2;
	}


	public void setAddress2(String address2) {
		this.address2 = address2;
	}


	public String getCity() {
		return city;
	}


	public void setCity(String city) {
		this.city = city;
	}


	public String getState() {
		return state;
	}


	public void setState(String state) {
		this.state = state;
	}


	public String getZipCode() {
		return zipCode;
	}


	public void setZipCode(String zipCode) {
		this.zipCode = zipCode;
	}


	public String getCountry() {
		return country;
	}


	public void setCountry(String country) {
		System.out.println("SetCountry = " + country);
		this.country = country;
	}


	public boolean isAcceptLicense() {
		return acceptLicense;
	}


	public void setAcceptLicense(boolean acceptLicense) {
		this.acceptLicense = acceptLicense;
	}


	public String getOs() {
		return os;
	}


	public void setOs(String os) {
		this.os = os;
	}


	public boolean isJoinMailingList() {
		return joinMailingList;
	}


	public void setJoinMailingList(boolean joinMailingList) {
		this.joinMailingList = joinMailingList;
	}
	  
	List<SelectItem> items;
	public List<SelectItem> getCountries(){
		if (items == null){
			items = new ArrayList<SelectItem>();
			for (String c: countries){
				items.add(new SelectItem(c));		
			}
		}
		return items;
	}
	
	public void validateEmail(FacesContext context, UIComponent component, Object value) throws ValidatorException {
	    if (!(email.contains("@"))) {
	        String message = "Please enter a valid emailS.";
	        FacesMessage facesMessage = new FacesMessage(FacesMessage.SEVERITY_ERROR, message, message);
	        ((UIInput) component).setValid(false);
	        throw new ValidatorException(facesMessage);
	    }
	}

	public boolean isError() {
		return error;
	}

	public void setError(boolean error) {
		this.error = error;
	}

}
