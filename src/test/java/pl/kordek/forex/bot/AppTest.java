package pl.kordek.forex.bot;

import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.TradingRecord;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Unit test for simple App.
 */
public class AppTest 
    extends TestCase
{
    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public AppTest( String testName )
    {
        super( testName );
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite( AppTest.class );
    }

    /**
     * Rigourous Test :-)
     */
    public void testApp()
    {

        TradingRecord longTradingRecord = new BaseTradingRecord();
        longTradingRecord.enter(0);
        longTradingRecord.exit(2);
        assertTrue(longTradingRecord.getCurrentPosition().isNew());
   //     assertTrue(longTradingRecord.getLastTrade().isClosed());
       // assertTrue(longTradingRecord.get);
        longTradingRecord.enter(4);
        assertFalse(longTradingRecord.getCurrentPosition().isNew());
        
        longTradingRecord.exit(6);
        assertTrue(longTradingRecord.getCurrentPosition().isNew());
    }
}
