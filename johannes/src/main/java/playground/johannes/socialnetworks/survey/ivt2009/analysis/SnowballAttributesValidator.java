/* *********************************************************************** *
 * project: org.matsim.*
 * SnowballAttributesValidator.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2010 by the members listed in the COPYING,        *
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
package playground.johannes.socialnetworks.survey.ivt2009.analysis;

import org.matsim.contrib.sna.snowball.SampledGraph;
import org.matsim.contrib.sna.snowball.SampledVertex;

import playground.johannes.socialnetworks.snowball2.social.SocialSampledGraphProjection;
import playground.johannes.socialnetworks.survey.ivt2009.graph.SocialSparseEdge;
import playground.johannes.socialnetworks.survey.ivt2009.graph.SocialSparseGraph;
import playground.johannes.socialnetworks.survey.ivt2009.graph.SocialSparseVertex;
import playground.johannes.socialnetworks.survey.ivt2009.graph.io.GraphReaderFacade;

/**
 * @author illenberger
 *
 */
public class SnowballAttributesValidator implements GraphValidator<SampledGraph> {

	@Override
	public boolean validate(SampledGraph graph) {
		for(SampledVertex vertex : graph.getVertices()) {
			if(vertex.isSampled()) {
				int it_i = vertex.getIterationSampled();
				for(SampledVertex neighbor : vertex.getNeighbours()) {
					if(neighbor.isSampled()) {
						int it_j = neighbor.getIterationSampled();
//						if(it_j + 1 != it_i && it_j - 1 != it_i && it_i != it_j)
//							return false;
					}
				}
				
				if(it_i != vertex.getIterationDetected() + 1)
					return false;
			} else {
				int it_i = vertex.getIterationDetected();
				boolean found = false;
				for(SampledVertex neighbor : vertex.getNeighbours()) {
					if(it_i > neighbor.getIterationSampled())
						return false;
					if(it_i == neighbor.getIterationSampled())
						found = true;
				}
				if(!found)
					return false;
			}
		}
		
		return true;
	}

	public static void main(String args[]) {
		SocialSampledGraphProjection<SocialSparseGraph, SocialSparseVertex, SocialSparseEdge> graph = GraphReaderFacade.read("/Users/jillenberger/Work/work/socialnets/data/ivt2009/raw/05-2010/graph/graph.graphml");
		
//		SnowballAttributesValidator validator = new SnowballAttributesValidator();
//		if(validator.validate(graph)) {
//			System.out.println("ok");
//		} else {
//			System.out.println("error");
//		}
		
		ComponentValidator validator = new ComponentValidator();
		if (validator.validate(graph)) {
			System.out.println("ok");
		} else {
			System.out.println("error");
		}
	}
			
	
}
