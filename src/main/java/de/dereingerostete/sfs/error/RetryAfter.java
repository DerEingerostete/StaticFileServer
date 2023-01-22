package de.dereingerostete.sfs.error;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@EqualsAndHashCode(callSuper = true)
public class RetryAfter extends AdditionalInfo{
    protected final long seconds;

    public RetryAfter(long seconds) {
        super("Retry-After");
        this.seconds = seconds;
    }

}
