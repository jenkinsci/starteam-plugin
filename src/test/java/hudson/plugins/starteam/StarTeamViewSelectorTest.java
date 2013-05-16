package hudson.plugins.starteam;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.text.ParseException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;

import com.starbase.starteam.View;

public class StarTeamViewSelectorTest {

	@Rule
	public ErrorCollector collector = new ErrorCollector();

/**
 * By default the selector should pick CURRENT
 */
	@Test
	public final void testDefaultValues() throws ParseException {
		StarTeamViewSelector selector = new StarTeamViewSelector(null,null);
		assertNull(selector.getConfigInfo());
		assertEquals("CURRENT", selector.getConfigType());
		
		selector = new StarTeamViewSelector(null,"");
		assertNull(selector.getConfigInfo());
		assertEquals("CURRENT", selector.getConfigType());
		
		selector = new StarTeamViewSelector(null,"CURRENT");
		assertNull(selector.getConfigInfo());
		assertEquals("CURRENT", selector.getConfigType());
	}

	/**
	 * Time should be in correct format 
	 */
	@Test (expected = NullPointerException.class )
	public final void testNullTime() throws ParseException {
		@SuppressWarnings("unused")
		StarTeamViewSelector selector = new StarTeamViewSelector(null,"TIME");
		fail("Null Time Should not be accepted");
	}

	/**
	 * Time should be in correct format 
	 */
	@Test (expected = ParseException.class )
	public final void testEmptyTime() throws ParseException {
		@SuppressWarnings("unused")
		StarTeamViewSelector selector = new StarTeamViewSelector("","TIME");
		fail("Empty Time Should not be accepted");
	}
	/**
	 * Time should be in correct format 
	 */
	@Test
	public final void testTimeValue() throws ParseException {
		StarTeamViewSelector selector = new StarTeamViewSelector("1970/12/24 01:02:03","TIME");
		assertEquals("1970/12/24 01:02:03",selector.getConfigInfo());
		assertEquals("TIME", selector.getConfigType());
	}

	/**
	 * Label should be accepted 
	 */
	@Test
	public final void testLabels() throws ParseException {
		StarTeamViewSelector selector = new StarTeamViewSelector(null,"LABEL");
		assertNull(selector.getConfigInfo());
		assertEquals("LABEL", selector.getConfigType());
		selector = new StarTeamViewSelector("","LABEL");
		assertEquals("",selector.getConfigInfo());
		assertEquals("LABEL", selector.getConfigType());
	}

	/**
	 * Promotion label should be accepted
	 */
	@Test
	public final void testPromotionValue() throws ParseException {
		StarTeamViewSelector selector = new StarTeamViewSelector(null,"PROMOTION");
		assertNull(selector.getConfigInfo());
		assertEquals("PROMOTION", selector.getConfigType());
		selector = new StarTeamViewSelector("","PROMOTION");
		assertEquals("",selector.getConfigInfo());
		assertEquals("PROMOTION", selector.getConfigType());
	}

	@Test ( expected = IllegalArgumentException.class )
	public final void testConfigView() throws ParseException, StarTeamSCMException {
		StarTeamViewSelector selector =new StarTeamViewSelector(null,"LABEL");
		View baseView = null;
		selector.configView(baseView, -1);
		fail("Configuring null view should blow up.");
	}

	@Test
	public final void testExpandLabelPattern() throws ParseException {
		String[][] tests = new String[][] {
			{"", ""},
			{"L1", "L1"},
			{"L1%", "L1%"},
			{"%{d:BuildNumber}", "123"},
			{"%{05d:BuildNumber}", "00123"},
			{"%{x:BuildNumber}", "7b"},
			{"%{X:BuildNumber}", "7B"},
			{"%{04x:BuildNumber}", "007b"},
			{"%{#x:BuildNumber}", "0x7b"},
			{"prefix.%{d:BuildNumber}.suffix", "prefix.123.suffix"},
			{"%{d:BuildNumber}-%{x:BuildNumber}", "123-7b"},
		};
		for (int i = 0; i < tests.length; i++) {
			String test = tests[i][0];
			String want = tests[i][1];
			String got = StarTeamViewSelector.expandLabelPattern(test, 123);
			collector.checkThat("expandLabelPattern("+test+", 123)", got, equalTo(want));
		}
	}

}
