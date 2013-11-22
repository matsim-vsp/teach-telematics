/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2013 by the members listed in the COPYING,     *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package playground.southafrica.projects.complexNetworks.pathDependence;

import java.util.Stack;

import org.matsim.api.core.v01.Id;
import org.matsim.core.basic.v01.IdImpl;
import org.matsim.core.utils.geometry.CoordImpl;
import org.matsim.core.utils.io.MatsimXmlParser;
import org.xml.sax.Attributes;

public class DigicorePathDependentNetworkReader_v1 extends MatsimXmlParser {
	private final static String NETWORK = "digicoreNetwork";
	private final static String NODE = "node";
	private final static String PRECEDING = "preceding";
	private final static String FOLLOWING = "following";
	
	/* Attributes. */
	private final static String ATTR_DESCR = "desc";
	private final static String ATTR_NODE_ID = "id";
	private final static String ATTR_X = "x";
	private final static String ATTR_Y = "y";
	private final static String ATTR_PRECEDING_ID = "id";
	private final static String ATTR_FOLLOWING_ID = "id";
	private final static String ATTR_WEIGHT = "weight";
	
	private PathDependentNetwork network = null;
	
	private Id currentNodeId = null;
	private Id currentPrecedingId = null;
	
	
	/**
	 * Executes the path-dependent network reader.
	 * @param args
	 */
	public static void main(String[] args){
		String input = args[0];
		DigicorePathDependentNetworkReader_v1 nr = new DigicorePathDependentNetworkReader_v1();
		nr.parse(input);
		nr.network.writeNetworkStatisticsToConsole();
	}
	
	
	public DigicorePathDependentNetworkReader_v1() {

	}

	@Override
	public void startTag(String name, Attributes atts, Stack<String> context) {
		if(NETWORK.equals(name)){
			network = new PathDependentNetwork();
			String descr = atts.getValue(ATTR_DESCR);
			if(descr != null){
				network.setDescription(descr);
			}
		} else if(NODE.equals(name)){
			currentNodeId = new IdImpl(atts.getValue(ATTR_NODE_ID));
			String x = atts.getValue(ATTR_X);
			String y = atts.getValue(ATTR_Y);
			network.addNewPathDependentNode(currentNodeId, new CoordImpl(x, y));
		} else if(PRECEDING.equals(name)){
			currentPrecedingId = new IdImpl(atts.getValue(ATTR_PRECEDING_ID));
		} else if (FOLLOWING.equals(name)){
			Id nextId = new IdImpl(atts.getValue(ATTR_FOLLOWING_ID));
			double weight = Double.parseDouble(atts.getValue(ATTR_WEIGHT));
			this.network.setPathDependentEdgeWeight(currentPrecedingId, currentNodeId, nextId, weight);
		} else{
			throw new RuntimeException(this + "[tag=" + name + " not known or supported]");
		}
	}

	@Override
	public void endTag(String name, String content, Stack<String> context) {
		if(NETWORK.equals(name)){
			/* Do nothing. */
		} else if(NODE.equals(name)){
			currentNodeId = null;
		} else if(PRECEDING.equals(name)){
			currentPrecedingId = null;
		} else if (FOLLOWING.equals(name)){
			/* Do nothing. */
		} else{
			throw new RuntimeException(this + "[tag=" + name + " not known or supported]");
		}
	}
	
	@Override
	protected void setDoctype(final String doctype) {
		super.setDoctype(doctype);
		// Currently the only version is v1
		if ("digicorePathDependentNetwork_v1.dtd".equals(doctype)) {
		} else {
			throw new IllegalArgumentException("Doctype \"" + doctype + "\" not known.");
		}
	}


}
