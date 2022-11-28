package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.Brand;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.Gear;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.GearOffer;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.Image;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SplatNetShopResult implements Serializable {
	private SplatNetShopResult.Data data;

	public List<GearOffer> getAllOffers() {
		var list = new ArrayList<GearOffer>();
		list.addAll(Arrays.asList(data.gesotown.pickupBrand.brandGears));
		list.addAll(Arrays.asList(data.gesotown.limitedGears));

		return list;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	// @Accessors(fluent = true)
	public static class Data implements Serializable {
		private Gesotown gesotown;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	// @Accessors(fluent = true)
	public static class Gesotown implements Serializable {
		private PickupBrand pickupBrand;
		private GearOffer[] limitedGears;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	// @Accessors(fluent = true)
	public static class PickupBrand implements Serializable {
		private Image image;
		private Brand brand;

		private GearOffer[] brandGears;
		private Brand nextBrand;

		private String saleEndTime;

		public Instant getSaleEndTimeAsInstant() {
			return Instant.parse(saleEndTime);
		}
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	// @Accessors(fluent = true)
	public static class GearNodes implements Serializable {
		private Gear[] nodes;
	}
}
