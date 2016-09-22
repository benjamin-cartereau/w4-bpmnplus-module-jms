package eu.w4.contrib.bpmnplus.module.jms.identification;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a simplified W4 BPMN+ User
 */
public class User {

  private String login;
  private String password;
  private final Map<String, Object> properties = new HashMap<String, Object>();
  private final Map<String, String> attributes = new HashMap<String, String>();

  /**
   * Default constructor
   * @param login login of the User
   */
  public User(final String login) {
    this.login = login;
  }
  
  public String getLogin() {
    return login;
  }

  public void setLogin(String login) {
    this.login = login;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public Map<String, Object> getProperties() {
    return properties;
  }

  public Map<String, String> getAttributes() {
    return attributes;
  }

  public void setProperties(String name, Object value) {
    this.properties.put(name, value);
  }

  public void setAttributes(String name, String value) {
    this.attributes.put(name, value);
  }

  @Override
  public int hashCode() {
    int hash = 3;
    hash = 59 * hash + Objects.hashCode(this.login);
    return hash;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final User other = (User) obj;
    if (!Objects.equals(this.login, other.login)) {
      return false;
    }
    return true;
  }
  
  
}
