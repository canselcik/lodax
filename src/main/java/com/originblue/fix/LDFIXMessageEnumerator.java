package com.originblue.fix;

import com.paritytrading.philadelphia.FIXMessage;
import com.paritytrading.philadelphia.FIXValue;
import org.joda.time.MutableDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LDFIXMessageEnumerator {
    protected static Logger logger = LoggerFactory.getLogger(LDFIXMessageEnumerator.class);
    protected static String asString(FIXMessage m, int fixTag, boolean defaultOnMissing, boolean print, String printTag) {
        String toReturn = null;
        FIXValue v = null;
        if (m != null)
            v = m.findField(fixTag);
        if (v != null)
            toReturn = v.asString();
        else if (defaultOnMissing)
            toReturn = "<null>";
        if (print)
            logger.info(" -> {}: {}", printTag, toReturn == null ? "<null>" : toReturn);
        return toReturn;
    }

    protected static Double asFloat(FIXMessage m, int fixTag, boolean defaultOnMissing, boolean print, String printTag) {
        Double toReturn = null;
        FIXValue v = null;
        if (m != null)
            v = m.findField(fixTag);
        if (v != null)
            toReturn = v.asFloat();
        else if (defaultOnMissing)
            toReturn = new Double(0.0);
        if (print)
            logger.info(" -> {}: {}", printTag, toReturn == null ? "<null>" : toReturn);
        return toReturn;
    }

    protected static Character asChar(FIXMessage m, int fixTag, boolean defaultOnMissing, boolean print, String printTag) {
        Character toReturn = null;
        FIXValue v = null;
        if (m != null)
            v = m.findField(fixTag);
        if (v != null)
            toReturn = v.asChar();
        else if (defaultOnMissing)
            toReturn = new Character('0');
        if (print)
            logger.info(" -> {}: {}", printTag, toReturn == null ? "<null>" : toReturn);
        return toReturn;
    }

    protected static MutableDateTime asDateTime(FIXMessage m, int fixTag, boolean print, String printTag) {
        MutableDateTime toReturn = new MutableDateTime();
        FIXValue v = null;
        if (m != null)
            v = m.findField(fixTag);
        if (v != null)
            v.asDate(toReturn);
        if (print)
            logger.info(" -> {}: {}", printTag, toReturn == null ? "<null>" : toReturn);
        return toReturn;
    }
}
