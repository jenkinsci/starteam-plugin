/**
 *
 */
package hudson.plugins.starteam;

import java.io.Serializable;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.starbase.starteam.Label;
import com.starbase.starteam.PromotionState;
import com.starbase.starteam.View;
import com.starbase.starteam.ViewConfiguration;
import com.starbase.util.OLEDate;

/**
 * 
 * Contents of a View can be adjusted by selecting versions other then the tip. 
 * 
 * @author Jan Ruzicka
 *
 */
public class StarTeamViewSelector implements Serializable {
	private static final long serialVersionUID = 1L;
	
	/**
	 * Current: view brings the most recent or "tip" versions of items, 
	 * Label: view brings items with the specified label attached, 
	 * Time: view brings items designated by time,
	 * Promotion: view brings items designated by a promotion state, similar to label-based view.
	 */
	enum ConfigType {
		CURRENT, LABEL, TIME, PROMOTION
	};

	private final String configInfo;       // configuration information Label name, Promotion State name or date 
	private final ConfigType configType;   // type of configuration.
	private final DateFormat df;

	/**
	 * Default constructor
	 *
	 * @param configType 
	 * Current: view brings the most recent or "tip" versions of items, 
	 * Label: view brings items with the specified label attached, 
	 * Time: view brings items designated by time,
	 * Promotion: view brings items designated by a promotion state, similar to label-based view.
	 * @param configInfo 
	 * Information according to configuration type. Label name, Promotion State name or date 
	 * 
	 * @throws ParseException 
	 */
	public StarTeamViewSelector(String configInfo, String configType) throws ParseException {
		this.configInfo = configInfo;
		ConfigType result = ConfigType.CURRENT;
		if (configType != null)	{
			try{
				result = ConfigType.valueOf(configType.toUpperCase());
			} catch ( IllegalArgumentException ignored)	{
				// ignored exception - by default the type will go to CURRENT
			}
		}
		this.configType = result;
		this.df = new SimpleDateFormat("yyyy/M/d");

	    if (this.configType == ConfigType.TIME) {
	    	df.parse(configInfo);
	    }
	}
	
	public View configView(View baseView) throws StarTeamSCMException, ParseException{
		final ViewConfiguration configuration;

		if (configInfo != null && !configInfo.isEmpty()) {
			switch (configType) {
			case CURRENT:
				configuration = ViewConfiguration.createTip();
				break;
			case LABEL:
				int labelId = findLabelInView(baseView, configInfo);
				configuration = ViewConfiguration.createFromLabel(labelId);
				break;
			case PROMOTION:		          
				// note: If the promotion state is assigned to <<current>> then the resulting ID will be NULL and
				// we will revert to a view based on the current tip.
				Integer promotionStateId = findPromotionStateInView(baseView, configInfo);
				if (promotionStateId != null) {
					configuration = ViewConfiguration.createFromPromotionState(promotionStateId);
				} else {					
					configuration = ViewConfiguration.createTip();
				}
				break;
			case TIME:
	            Date effectiveDate = df.parse(configInfo);
				configuration = ViewConfiguration.createFromTime(new OLEDate(effectiveDate));
				break;
			default:
				throw new StarTeamSCMException("Could not construct view - no configuration provided");
			}
		} else {
			configuration = ViewConfiguration.createTip();
		}
		return new View(baseView, configuration);
	}

	private static int findLabelInView(final View view, final String labelname) throws StarTeamSCMException {
		for (Label label : view.getLabels()) {
			if (labelname.equals(label.getName())) {
				return label.getID();
			}
		}
		throw new StarTeamSCMException("Couldn't find label [" + labelname + "] in view " + view.getName());
	}

	private static Integer findPromotionStateInView(final View view, final String promotionState) throws StarTeamSCMException {
		for (PromotionState ps : view.getPromotionModel().getPromotionStates()) {
			if (promotionState.equals(ps.getName())) {
				if (ps.getLabelID() == -1) {
					// PROMOTION STATE is set to <<current>>
					return null;
				}
				return ps.getObjectID();
			}
		}
		throw new StarTeamSCMException("Couldn't find promotion state " + promotionState + " in view " + view.getName());
	}

	public String getConfigInfo() {
		return configInfo;
	}

	public String getConfigType() {
		return configType.name();
	}

}
