# Risks

  -----------------------------------------------------------------------------------
  ID          Risk            Impact      Likelihood   Mitigation         Status
  ----------- --------------- ----------- ------------ ------------------ -----------
  R-001       Accessibility   Critical    Medium       Health checks,     Open
              Service                                  recovery UX,       
              interrupted by                           device testing     
              user/system                                                 

  R-002       OEM background  Critical    Medium       OEM matrix and     Open
              restrictions                             reliability        
              affect                                   testing            
              enforcement                                                 

  R-003       Package         High        Medium       Validate narrow    Mitigated (POC-02A)
              visibility                               visibility         
              constraints                              approach in POC    
              complicate app                                              
              discovery                                                   

  R-004       UsageStats      High        Medium       UsageStats as      Open
              timing differs                           truth +            
              from                                     accessibility      
              enforcement                              access trigger     
              trigger                                                     

  R-005       Play policy     High        Medium       Recheck current    Open
              requirements                             policy before      
              change                                   release            

  R-006       Battery impact  Medium      Medium       Minimize           Open
              reduces                                  background work    
              retention                                and benchmark      

  R-007       Users distrust  High        Medium       Transparent        Open
              sensitive                                explanation and    
              permissions                              minimal data use   

  R-008       Blocking        Critical    Medium       Treat as           Open
              failures                                 release-blocking   
              undermine                                issue; real-device 
              product value                            testing            
  -----------------------------------------------------------------------------------

## Risk Rule

Critical enforcement risks must be addressed before production release.
