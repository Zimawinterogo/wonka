package dev.whiteout.power;

/** Pure validation result; coordinates are optional and never execute an action. */
public record PassportIssue(Severity severity,String code,String object,MapPassport.Position position) {
 public enum Severity { ERROR,WARN,INFO }
}
