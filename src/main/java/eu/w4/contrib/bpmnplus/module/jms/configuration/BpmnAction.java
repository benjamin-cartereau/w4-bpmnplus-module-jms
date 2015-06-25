package eu.w4.contrib.bpmnplus.module.jms.configuration;

/**
 * List of allowed W4 BPMN actions
 */
public enum BpmnAction {

  INSTANTIATE,
  SIGNAL;

  /**
   * Parse an action from a String
   * @param action name of the action
   * @return BpmnAction with this name (throws an IllegalArgumentException if not found)
   */
  public static BpmnAction parse(String action) {
    // Default value : INSTANTIATE
    if (action == null || action.trim().isEmpty()) {
      return INSTANTIATE;
    }
    else {
      String actionUpperCase = action.toUpperCase();
      for (BpmnAction value : values()) {
        if (value.toString().equals(actionUpperCase)) {
          return value;
        }
      }
    }
    throw new IllegalArgumentException("Unknown action " + action);
  }
}
