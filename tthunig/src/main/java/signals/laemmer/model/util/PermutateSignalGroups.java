package signals.laemmer.model.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.contrib.signals.model.Signal;
import org.matsim.contrib.signals.model.SignalGroup;
import org.matsim.contrib.signals.model.SignalSystem;
import org.matsim.lanes.data.Lane;

import signals.laemmer.model.SignalPhase;

public class PermutateSignalGroups {
	/**
	 * 
	 * Creates permutations of all list elements with a maximum length of length 
	 * 
	 * @param signalGroups list with signalGroups to permutate 
	 * @param length maximum length of permutations
	 * @return list of lists with all possible permutations, may content duplicates and empty lists
 	 * @author pschade
	 *
	 */
	private static ArrayList<ArrayList<SignalGroup>> permutate(ArrayList<SignalGroup> signalGroups, int length) {
		ArrayList<ArrayList<SignalGroup>> out = new ArrayList<>();
		
		//Abbruchbedingung, wenn nur noch Länge 1 alle erzeugen und zurückgeben
		if (length == 1) {
			for (SignalGroup e : signalGroups) {
				out.add(new ArrayList<SignalGroup>(Arrays.asList(e)));
			}
			return out;
		}
		
		//Wenn (Elementanzahl = Länge) ein Set erzeugen und hinzufügen

		if (length == signalGroups.size()) {
			ArrayList<SignalGroup> tmp = new ArrayList<>();
			for (SignalGroup e : signalGroups) {
				tmp.add(e);
			}
			out.add(tmp);
		}
		
		//wenn (Elementanzahl > Länge) 
		if (length < signalGroups.size()) {	
			for (int elementIdxToSkip = 0; elementIdxToSkip < signalGroups.size(); elementIdxToSkip++) {
				ArrayList<SignalGroup> tmp = new ArrayList<>();
				for (int curElementIdx=0; curElementIdx < signalGroups.size(); curElementIdx++) {
					if (curElementIdx!=elementIdxToSkip)
						tmp.add(signalGroups.get(curElementIdx));
				}
				out.add(tmp);
			}
		}

		// nun für oben erzeugte Subsets erneut aufrufen mit (länge-1)
		ArrayList<ArrayList<SignalGroup>> tmp = new ArrayList<>();
		for (ArrayList<SignalGroup> subList : out) {
			tmp.addAll(permutate(subList, length-1));
		}
		out.addAll(tmp);
		return out;
	}
	
	/**
	 * Removes duplicate combinations of signalGroups
	 * this should be moved to permutate(…) (with parameter?)
	 * @param permutationsWithDuplicates
	 * @return
	 * @author pschade
	 */
	private static ArrayList<ArrayList<SignalGroup>> removeDuplicates(ArrayList<ArrayList<SignalGroup>> permutationsWithDuplicates) {
		ArrayList<ArrayList<SignalGroup>> clearedPermutes = new ArrayList<>();
		for (ArrayList<SignalGroup> permut : permutationsWithDuplicates) {
			if (permut.size()==0)
				continue;
			boolean hasPermutInClearedList = false;
			for (ArrayList<SignalGroup> existPermut : clearedPermutes) {
				if (existPermut.equals(permut)) {
					hasPermutInClearedList = true;
					break;
				}
			}
			if(hasPermutInClearedList)
				continue;
			clearedPermutes.add(permut);
		}
		return clearedPermutes;
	}
	
	@Deprecated //use createPhasesFromSignalGroups instead
    private static ArrayList<ArrayList<SignalGroup>> createAllSignalPermutations(SignalSystem system, HashMap<Id<Lane>, Lane> lanemap) {
		ArrayList<SignalGroup> signalGroups = new ArrayList<>(system.getSignalGroups().values());
		ArrayList<ArrayList<SignalGroup>> allSignalGroupPerms = removeDuplicates(permutate(signalGroups, signalGroups.size()));
		
		//check for illegal combinations
		ArrayList<SignalGroup> illegalGroups = new ArrayList<>();
		for (ArrayList<SignalGroup> sgs : allSignalGroupPerms) {
			for (SignalGroup sg : sgs) {
				ArrayList<Id> lanesOfCurrSg = new ArrayList<>();
				for (Signal signal : sg.getSignals().values()) {
					lanesOfCurrSg.addAll(signal.getLaneIds());
				}
				for (Id<Lane> l : lanesOfCurrSg) {
					for (Id<Lane> illegalLane : ( (ArrayList<Id<Lane>>) (lanemap.get(l).getAttributes().getAttribute("conflictingLanes")) ) ) {
						if (lanesOfCurrSg.contains(illegalLane)) {
							illegalGroups.add(sg);
							break;
						}
					}
				}
			}
		}
		allSignalGroupPerms.removeAll(illegalGroups);
		return allSignalGroupPerms;
		
//		System.out.println("SIZE OF SIGNALGROUPPERMS: "+allSignalGroupPerms.size());
//		for (ArrayList<SignalGroup> sgs : allSignalGroupPerms) {
//			System.out.println();;
//		}
	}
	
    public static ArrayList<SignalPhase> createPhasesFromSignalGroups(SignalSystem system, Map<Id<Lane>, Lane> lanemap) {
		ArrayList<SignalGroup> signalGroups = new ArrayList<>(system.getSignalGroups().values());
		ArrayList<ArrayList<SignalGroup>> allSignalGroupPerms = removeDuplicates(permutate(signalGroups, signalGroups.size()));
		ArrayList<SignalPhase> validPhases = new ArrayList<>();
		//check for illegal combinations
		ArrayList<SignalGroup> illegalGroups = new ArrayList<>();
		for (ArrayList<SignalGroup> sgs : allSignalGroupPerms) {
			for (SignalGroup sg : sgs) {
				ArrayList<Id<Lane>> lanesOfCurrSg = new ArrayList<>();
				for (Signal signal : sg.getSignals().values()) {
					lanesOfCurrSg.addAll(signal.getLaneIds());
				}
				for (Id<Lane> l : lanesOfCurrSg) {
					for (Id<Lane> illegalLane : ( (ArrayList<Id<Lane>>) (lanemap.get(l).getAttributes().getAttribute("conflictingLanes")) ) ) {
						if (lanesOfCurrSg.contains(illegalLane)) {
							illegalGroups.add(sg);
							break;
						}
					}
				}
			}
		}
		allSignalGroupPerms.removeAll(illegalGroups);
		
		for(ArrayList<SignalGroup> sgs : allSignalGroupPerms) {
			SignalPhase newPhase = new SignalPhase();
			for (SignalGroup sg : sgs) {
				List<Id<Lane>> lanes = new LinkedList<>();
				for (Signal s : sg.getSignals().values()) {
					lanes.addAll(s.getLaneIds());
				}
				newPhase.addGreenSignalGroupsAndLanes(sg.getId(), lanes);
			}
		}
		
		return validPhases;
	}
}