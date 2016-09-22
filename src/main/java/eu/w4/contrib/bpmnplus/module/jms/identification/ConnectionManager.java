package eu.w4.contrib.bpmnplus.module.jms.identification;

import eu.w4.common.exception.CheckedException;
import eu.w4.contrib.bpmnplus.module.jms.exception.JMSModuleException;
import eu.w4.engine.client.EnginePrincipalState;
import eu.w4.engine.client.service.AuthenticationService;
import eu.w4.engine.client.service.EngineService;
import java.rmi.RemoteException;
import java.security.Principal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import javax.inject.Inject;
import javax.inject.Named;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.DisposableBean;

/**
 * Class that helps to manage W4 connections
 */
@Named
public class ConnectionManager implements DisposableBean {

  private static final Logger logger = LogManager.getLogger();

  // Cached user/principal mapping
  private final Map<User, Principal> usersPrincipals = Collections.synchronizedMap(new HashMap<User, Principal>());

  // W4 authentication service
  private final AuthenticationService authenticationService;

  @Inject
  public ConnectionManager(EngineService engineService) throws CheckedException, RemoteException {
    this.authenticationService = engineService.getAuthenticationService();
  }

  /**
   * Log on to W4 BPMN+ Engine or reuse principal if it exists and is valid
   *
   * @param user w4 user
   * @return Principal authenticated user principal
   */
  public Principal login(User user) {
    if (user == null) {
      throw new IllegalArgumentException("User cannot be null");
    }
    if (user.getLogin() == null) {
      throw new IllegalArgumentException("User login cannot be null");
    }

    Principal principal;
    synchronized (usersPrincipals) {
      principal = usersPrincipals.get(user);

      try {
        if (principal != null) {
          if (authenticationService.getEnginePrincipalState(principal).equals(EnginePrincipalState.VALID)) {
            logger.debug("No need to login, reuse existing valid principal for '{}'", principal.getName());
            return principal;
          } 
          else {
            logger.debug("Principal for '{}' found in cache, but not valid anymore", principal.getName());
          }
        }
        logger.info("Log on {} to W4 BPMN+ Engine", user.getLogin());
        principal = authenticationService.login(user.getLogin(), user.getPassword());
        usersPrincipals.put(user, principal);
      } catch (CheckedException cex) {
        logger.error("Error on login", cex);
        throw new JMSModuleException("Cannot login against engine", cex);
      } catch (RemoteException rex) {
        logger.error("Error on login", rex);
        throw new JMSModuleException("Cannot login against engine", rex);
      }
    }
    return principal;
  }

  /**
   * Log out from W4 BPMN+ Engine
   *
   * @param user user to log out
   */
  public void logout(User user) {
    if (user == null) {
      return;
    }

    synchronized (usersPrincipals) {
      Principal principal = usersPrincipals.get(user);
      if (principal == null) {
        logger.debug("User {} cannot be found from cache (cannot log him out from W4 BPMN+ Engine)", user.getLogin());
        return;
      }

      logger.info("Log out {} from W4 BPMN+ Engine", user.getLogin());
      try {
        usersPrincipals.remove(user);
        authenticationService.logout(principal);
      } catch (CheckedException cex) {
        // Not an error if principal has already been logged out
        //logger.error("Error on logout", cex);
        //throw new JMSModuleException("Cannot sign out of the engine", cex);
        logger.debug("Cannot logout ({})", cex.getClass().getName());
      } catch (RemoteException rex) {
        logger.error("Error on logout (remote access to the engine)", rex);
        throw new JMSModuleException("Cannot sign out of the engine", rex);
      }
    }
  }

  /**
   * Log out from W4 BPMN+ Engine
   *
   * @param principal authenticated user principal
   */
  public void logout(Principal principal) {
    if (principal == null) {
      return;
    }
    logger.info("Log out {} from W4 BPMN+ Engine", principal.getName());
    synchronized (usersPrincipals) {
      try {
        removePrincipal(principal);
        authenticationService.logout(principal);
      } catch (CheckedException cex) {
        // Not an error if principal has already been logged out
        //logger.error("Error on logout", cex);
        //throw new JMSModuleException("Cannot sign out of the engine", cex);
        logger.debug("Cannot logout ({})", cex.getClass().getName());
      } catch (RemoteException rex) {
        logger.error("Error on logout (remote access to the engine)", rex);
        throw new JMSModuleException("Cannot sign out of the engine", rex);
      }
    }
  }

  /**
   * Log out everybody from W4 BPMN+ Engine
   */
  private void logoutEverybody() {
    for (Principal principal : usersPrincipals.values()) {
      logout(principal);
    }
  }

  /**
   * Remove the entry having the principal for value
   *
   * @param principal Principal to remove
   */
  private void removePrincipal(Principal principal) {
    Iterator<Entry<User, Principal>> entryIterator = usersPrincipals.entrySet().iterator();
    while (entryIterator.hasNext()) {
      Entry<User, Principal> entry = entryIterator.next();
      if (principal.equals(entry.getValue())) {
        entryIterator.remove();
        break;
      }
    }
  }

  @Override
  public void destroy() throws Exception {
    logoutEverybody();
  }
}
