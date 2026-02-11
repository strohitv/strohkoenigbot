package tv.strohi.twitch.strohkoenigbot.splatoon3saver.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public enum IconBadgeNames {
	X_BATTLE(List.of(
		"Top 10 in X Battle!",
		"Top 10 X Battle Rank",
		"Won 350 Straight X Battles with X Power at 2,000+",
		"Top 500 in X Battle!",
		"Top 500 X Battle Rank",
		"Won 70 Straight X Battles with X Power at 2,000+",
		"Top 1,000 X Battle Rank",
		"Won 15 Straight X Battles with X Power at 2,000+",
		"Top 3,000 in X Battle!",
		"Top 3,000 X Battle Rank",
		"Top 5,000 X Battle Rank",
		"Top 10,000 X Battle Rank",
		"Top 30,000 X Battle Rank",
		"Top 50,000 X Battle Rank"
	)),
	ANARCHY_OPEN(List.of(
//		"S+ Rank Reached!",
//		"S Rank Reached!",
		"A Rank Reached!"
	)),
	ANARCHY_SERIES(List.of(
		"Won 250 Straight Anarchy Battles (Series)",
		"Won 50 Straight Anarchy Battles (Series)",
		"Won 10 Straight Anarchy Battles (Series)",
		"S+ Rank Reached!"
	)),
	CHALLENGE(List.of(
		"Top 5% in a Challenge!",
		"Top 20% in a Challenge!",
		"Top 50% in a Challenge!"
	)),
	SPLATFEST(List.of(
		"Ruler of Splatfest Reached!",
		"Won Ten 10x Battles",
		"Competed in Three 10x Battles"
	)),
	RULES(List.of(
		"1,200 Turf War Wins",
		"250 Turf War Wins",
		"50 Turf War Wins",
		"1,000 Splat Zones Wins",
		"100 Splat Zones Wins",
		"1,000 Tower Control Wins",
		"100 Tower Control Wins",
		"1,000 Rainmaker Wins",
		"100 Rainmaker Wins",
		"1,000 Clam Blitz Wins",
		"100 Clam Blitz Wins"
	)),
	MAIN(List.of(
		"10★ %s User",
		"9★ %s User",
		"8★ %s User",
		"7★ %s User",
		"6★ %s User",
		"5★ %s User",
		"4★ %s User"
	)),
	SPECIAL(List.of(
		"1,200 Wins with %s",
		"180 Wins with %s",
		"30 Wins with %s"
	));

	@Getter
	private final List<String> badgeNames;
}
