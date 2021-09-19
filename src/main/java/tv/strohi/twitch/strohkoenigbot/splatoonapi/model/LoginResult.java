package tv.strohi.twitch.strohkoenigbot.splatoonapi.model;


public class LoginResult {
	private FParamResult result;

	public FParamResult getResult() {
		return result;
	}

	public void setResult(FParamResult result) {
		this.result = result;
	}

	public static class FParamResult {
		private String f;
		private String p1;
		private String p2;
		private String p3;

		public String getF() {
			return f;
		}

		public void setF(String f) {
			this.f = f;
		}

		public String getP1() {
			return p1;
		}

		public void setP1(String p1) {
			this.p1 = p1;
		}

		public String getP2() {
			return p2;
		}

		public void setP2(String p2) {
			this.p2 = p2;
		}

		public String getP3() {
			return p3;
		}

		public void setP3(String p3) {
			this.p3 = p3;
		}
	}
}
