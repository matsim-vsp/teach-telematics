package playground.wrashid.msimoni.analyses;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.matsim.api.core.v01.Id;
import org.matsim.core.api.experimental.events.AgentArrivalEvent;
import org.matsim.core.api.experimental.events.LinkEnterEvent;
import org.matsim.core.api.experimental.events.handler.AgentArrivalEventHandler;
import org.matsim.core.api.experimental.events.handler.LinkEnterEventHandler;

public class CordonOutflowCollector implements AgentArrivalEventHandler,
		LinkEnterEventHandler {

	private HashSet<Id> agentsInCordon;
	private HashSet<Id> linksInsideCordon;
	private int binSizeInSeconds;
	private int[] outflowTimeBins;

	public CordonOutflowCollector(Set<Id> linksInsideCordon,
			int binSizeInSeconds) {
		this.linksInsideCordon = new HashSet<Id>();
		this.linksInsideCordon.addAll(linksInsideCordon);
		this.binSizeInSeconds = binSizeInSeconds;
		agentsInCordon = new HashSet<Id>();
		setOutflowTimeBins(new int[(3600 * 24 / binSizeInSeconds)]);
	}

	@Override
	public void reset(int iteration) {

	}

	private void increaseBinCount(double time) {
		int timeBinIndex=(int) ((Math.round(time) % (3600 * 24)) / binSizeInSeconds);
		if (timeBinIndex>=getOutflowTimeBins().length){
			System.out.println();
		}
		getOutflowTimeBins()[timeBinIndex]++;
	}

	@Override
	public void handleEvent(AgentArrivalEvent event) {
		if (linksInsideCordon.contains(event.getLinkId())) {
			increaseBinCount(event.getTime());
		}
	}

	@Override
	public void handleEvent(LinkEnterEvent event) {
		if (linksInsideCordon.contains(event.getLinkId())) {
			agentsInCordon.add(event.getPersonId());
		} else {
			if (agentsInCordon.contains(event.getPersonId())) {
				agentsInCordon.remove(event.getPersonId());
				increaseBinCount(event.getTime());
			}
		}
	}
	
	public void printOutflowTimeBins(){
		for (int i=0;i<outflowTimeBins.length;i++){
			System.out.println(i + "\t" + outflowTimeBins[i]);
		}
	}

	public int[] getOutflowTimeBins() {
		return outflowTimeBins;
	}

	private void setOutflowTimeBins(int[] outflowTimeBins) {
		this.outflowTimeBins = outflowTimeBins;
	}

}
