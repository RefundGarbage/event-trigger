package gg.xp.events.jails;

import gg.xp.events.Event;
import gg.xp.events.EventContext;
import gg.xp.events.actlines.data.Job;
import gg.xp.events.actlines.events.AbilityUsedEvent;
import gg.xp.events.actlines.events.WipeEvent;
import gg.xp.events.debug.DebugCommand;
import gg.xp.events.models.XivAbility;
import gg.xp.events.models.XivCombatant;
import gg.xp.events.models.XivPlayerCharacter;
import gg.xp.events.state.XivState;
import gg.xp.scan.HandleEvents;
import gg.xp.speech.CalloutEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class JailSolver {
	private static final Logger log = LoggerFactory.getLogger(JailSolver.class);

	private final List<XivPlayerCharacter> jailedPlayers = new ArrayList<>();

	@HandleEvents
	public void amTest(EventContext<Event> context, DebugCommand event) {
		XivState xivState = context.getStateInfo().get(XivState.class);
		List<XivPlayerCharacter> partyList = xivState.getPartyList();
		if (event.getCommand().equals("jailtest")) {
			List<String> args = event.getArgs();
			args.subList(1, args.size())
					.stream()
					.mapToInt(Integer::parseInt)
					.forEach(playerNum -> {
						int actualIndex = playerNum - 1;
						XivPlayerCharacter player;
						try {
							player = partyList.get(actualIndex);
						}
						catch (IndexOutOfBoundsException e) {
							log.error("You do not have {} players in the party. Are you in-instance?", playerNum);
							return;
						}
						jailedPlayers.add(player);
						// Fire off new event if we have exactly 3 events
						if (jailedPlayers.size() == 3) {
							context.accept(new UnsortedTitanJailsSolvedEvent(new ArrayList<>(jailedPlayers)));
						}
					});
		}
	}

	@HandleEvents
	public void handleWipe(EventContext<Event> context, WipeEvent event) {
		log.info("Cleared jails");
		jailedPlayers.clear();
	}

	@HandleEvents
	public void amResetManual(EventContext<Event> context, DebugCommand event) {
		if (event.getCommand().equals("jailreset")) {
			log.info("Cleared jails");
			jailedPlayers.clear();
		}
	}

	@HandleEvents
	public void handleJailCast(EventContext<Event> context, AbilityUsedEvent event) {
		// Check ability ID - we only care about these two
		long id = event.getAbility().getId();
		if (id != 0x2B6B && id != 0x2B6C) {
			return;
		}
		XivCombatant target = event.getTarget();
		if (target instanceof XivPlayerCharacter) {
			jailedPlayers.add((XivPlayerCharacter) target);
		}
		// Fire off new event if we have exactly 3 events
		if (jailedPlayers.size() == 3) {
			context.accept(new UnsortedTitanJailsSolvedEvent(new ArrayList<>(jailedPlayers)));
		}
	}

	@HandleEvents
	public void sortTheJails(EventContext<Event> context, UnsortedTitanJailsSolvedEvent event) {
		// This is where we would do job prio, custom prio, or whatever else you can come up with
		List<XivPlayerCharacter> jailedPlayers = new ArrayList<>(event.getJailedPlayers());
		jailedPlayers.sort(Comparator.comparing(player -> {
			Job job = player.getJob();
			if (job.isMeleeDps()) {
				return 1;
			}
			if (job.isTank()) {
				return 2;
			}
			if (job.isCaster()) {
				return 3;
			}
			if (job.isPranged()) {
				return 4;
			}
			if (job.isHealer()) {
				return 5;
			}
			// Shouldn't happen
			log.warn("Couldn't determine jail prio for player {} job {}", player, job);
			return 6;
		}));
		context.accept(new FinalTitanJailsSolvedEvent(jailedPlayers));
		log.info("Unsorted jails: {}", event.getJailedPlayers());
		log.info("Sorted jails: {}", jailedPlayers);
	}

	@HandleEvents
	public static void personalCallout(EventContext<Event> context, FinalTitanJailsSolvedEvent event) {
		XivPlayerCharacter me = context.getStateInfo().get(XivState.class).getPlayer();
		List<XivPlayerCharacter> jailedPlayers = event.getJailedPlayers();
		int i = jailedPlayers.indexOf(me);
		log.info("Jail index of player: {}", i);
		switch (i) {
			case 0:
				context.accept(new CalloutEvent("One"));
				break;
			case 1:
				context.accept(new CalloutEvent("Two"));
				break;
			case 2:
				context.accept(new CalloutEvent("Three"));
				break;
		}

	}

	@HandleEvents
	public static void automarks(EventContext<Event> context, FinalTitanJailsSolvedEvent event) {
		log.info("Marking jailed players");
		List<XivPlayerCharacter> playersToMark = event.getJailedPlayers();
		context.accept(new AutoMarkRequest(playersToMark.get(0)));
		context.accept(new AutoMarkRequest(playersToMark.get(1)));
		context.accept(new AutoMarkRequest(playersToMark.get(2)));
	}
}