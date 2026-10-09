# Risks

  -------------------------------------------------------------------------------------------------------------------------
  ID          Risk            Impact      Likelihood   Mitigation                                       Status
  ----------- --------------- ----------- ------------ ------------------------------------------------ -------------------
  R-001       Accessibility   Critical    Medium       `AccessibilityServiceHealthRepository` checks,   Open (Active POC-04)
              Service                      `ACCESSIBILITY_INTERRUPTED` state detection,      
              interrupted by               recovery UX, OEM background testing             
              user/system/OEM                                                               

  R-002       OEM background  Critical    Medium       OEM device matrix testing (Xiaomi, Samsung,      Open
              restrictions                 Oppo, Vivo) and reliability guidance             
              affect                                                                        
              enforcement                                                                   

  R-003       Package         High        Medium       Launcher query flag `0` with explicit `<queries>` Mitigated (POC-02A)
              visibility                   manifest declaration                             
              constraints                                                                   

  R-004       UsageStats      High        Low          UsageStatsManager is single source of usage;     Mitigated (POC-03/04/06)
              timing vs                                AccessibilityService is foreground detection;    
              enforcement                              UsageEvents reconstructs live ongoing intervals  
              trigger                                  without OS buffer lag (TD-013)                   

  R-005       Play policy     High        Medium       Explicit in-app prominent disclosure screen,     Open
              requirements                 no claim as accessibility tool (`isAccessibilityTool="false"`),
              on Accessibility             `canRetrieveWindowContent="false"`, narrow package filter

  R-006       Battery impact  Medium      Low          Narrow event subscription (`TYPE_WINDOW_STATE_CHANGED`), Mitigated (POC-05A)
              reduces                      5s check strictly scoped to active foreground    
              retention                    restricted app (0 checks anywhere else)          

  R-007       Users distrust  High        Medium       In-app prominent disclosure explaining strict privacy Open (POC-04)
              sensitive                    guarantee (no screen/text/password collection)   
              permissions                                                                   

  R-008       Blocking        Critical    Medium       Treat as release-blocking issue; physical device Open (Active POC-06)
              failures                     testing                                          

  R-009       OEM service     High        Medium       Detect service unbind/destroy and transition to   Open (POC-04)
              restart /                    `ACCESSIBILITY_NOT_GRANTED` state with user recovery
              kill after                   path                                             
              memory pressure                                                               

  R-010       OEM background  High        Medium       Standard Activity start with `FLAG_ACTIVITY_NEW_TASK` Open (POC-05)
              activity launch              works on AOSP/Samsung; document OEM popup permission
              restrictions                 toggles (Xiaomi/Oppo) in testing matrix.

  R-011       Transient window High       Low          Ignore 'android', 'com.android.systemui',        Mitigated (POC-06)
              interrupts live                          and enabled IMEs during active monitoring       
              monitoring                               without cancelling loop (TD-013)                 
  -------------------------------------------------------------------------------------------------------------------------

## Risk Rule

Critical enforcement risks must be addressed before production release. Do not mark a risk mitigated without empirical evidence.
