package org.matsim.core.scoring.functions;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.scoring.ScoringFunction;
import org.matsim.core.scoring.ScoringFunctionFactory;
import org.matsim.core.scoring.SumScoringFunction;

/**
 * A factory to create scoring functions as described by D. Charypar and K.
 * Nagel.
 * 
 * <blockquote>
 * <p>
 * Charypar, D. und K. Nagel (2005) <br>
 * Generating complete all-day activity plans with genetic algorithms,<br>
 * Transportation, 32 (4) 369-397.
 * </p>
 * </blockquote>
 * 
 * @author gunnar based on rashid_waraich
 */
public final class RandomizedScoringFunctionFactory implements
		ScoringFunctionFactory {

	protected Network network;

	private final ScoringParametersForPerson params;

	public RandomizedScoringFunctionFactory(final Scenario sc) {
		this(new RandomizedScoringParameters(sc), sc.getNetwork());
	}

	RandomizedScoringFunctionFactory(
			final ScoringParametersForPerson params,
			Network network) {
		this.params = params;
		this.network = network;
	}

	/**
	 *
	 * In every iteration, the framework creates a new ScoringFunction for each
	 * Person. A ScoringFunction is much like an EventHandler: It reacts to
	 * scoring-relevant events by accumulating them. After the iteration, it is
	 * asked for a score value.
	 *
	 * Since the factory method gets the Person, it can create a ScoringFunction
	 * which depends on Person attributes. This implementation does not.
	 *
	 * <li>The fact that you have a person-specific scoring function does not
	 * mean that the "creative" modules (such as route choice) are
	 * person-specific. This is not a bug but a deliberate design concept in
	 * order to reduce the consistency burden. Instead, the creative modules
	 * should generate a diversity of possible solutions. In order to do a
	 * better job, they may (or may not) use person-specific info. kai, apr'11
	 * </ul>
	 * 
	 * @param person
	 * @return new ScoringFunction
	 */
	@Override
	public ScoringFunction createNewScoringFunction(Person person) {

		final ScoringParameters parameters = params
				.getScoringParameters(person);

		SumScoringFunction sumScoringFunction = new SumScoringFunction();
		sumScoringFunction.addScoringFunction(new CharyparNagelActivityScoring(
				parameters));
		sumScoringFunction.addScoringFunction(new CharyparNagelLegScoring(
				parameters, this.network));
		sumScoringFunction.addScoringFunction(new CharyparNagelMoneyScoring(
				parameters));
		sumScoringFunction
				.addScoringFunction(new CharyparNagelAgentStuckScoring(
						parameters));
		return sumScoringFunction;
	}
}
