package hudson.plugins.starteam;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.text.ParseException;

import org.junit.Test;

import com.starbase.starteam.View;

public class StarTeamViewSelectorTest {

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
		StarTeamViewSelector selector = new StarTeamViewSelector(null,"TIME");
		fail("Null Time Should not be accepted");
	}

	/**
	 * Time should be in correct format 
	 */
	@Test (expected = ParseException.class )
	public final void testEmptyTime() throws ParseException {
		StarTeamViewSelector selector = new StarTeamViewSelector("","TIME");
		fail("Empty Time Should not be accepted");
	}
	/**
	 * Time should be in correct format 
	 */
	@Test
	public final void testTimeValue() throws ParseException {
		StarTeamViewSelector selector = new StarTeamViewSelector("1970/12/24","TIME");
		assertEquals("1970/12/24",selector.getConfigInfo());
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
		selector.configView(baseView);
		fail("Configuring null view should blow up.");
	}

}
