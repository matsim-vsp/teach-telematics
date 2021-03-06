/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2015 by the members listed in the COPYING,        *
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

package playground.ikaddoura.router;

import org.matsim.analysis.vtts.VTTSHandler;
import org.matsim.analysis.vtts.VTTScomputation;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;

/**
* @author ikaddoura
*/

public class RunVTTSRouter {

	public static void main(String[] args) {

		Config config = ConfigUtils.createConfig();
		Controler controler = new Controler(config);

		final VTTSHandler vttsHandler = new VTTSHandler(controler.getScenario(), new String[] {"non_network_walk", "transit_walk", "access_walk", "egress_walk"}, "interaction");
		final VTTSTimeDistanceTravelDisutilityFactory factory = new VTTSTimeDistanceTravelDisutilityFactory(vttsHandler, config);
		
		controler.addOverridingModule(new AbstractModule(){
			@Override
			public void install() {
				this.bindCarTravelDisutilityFactory().toInstance( factory );
			}
		}); 
		
		controler.addControlerListener(new VTTScomputation(vttsHandler));
		controler.run();
	}

}

