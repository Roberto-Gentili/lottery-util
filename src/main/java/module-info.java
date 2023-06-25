module testing {

    requires jdk.unsupported;
	requires org.apache.poi.ooxml;
	requires com.fasterxml.jackson.databind;
	requires org.jsoup;
	requires org.apache.poi.poi;
	requires org.apache.xmlbeans;

	opens org.rg.game.lottery.application to com.fasterxml.jackson.databind;

}
