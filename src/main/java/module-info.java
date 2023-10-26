module org.rg.game.lottery {

	requires com.fasterxml.jackson.databind;
	requires com.formdev.flatlaf;
	requires java.desktop;
	requires java.logging;
	requires javafx.graphics;
    requires jdk.unsupported;
	requires org.apache.poi.ooxml;
	requires org.apache.poi.poi;
	requires org.apache.xmlbeans;
	requires org.burningwave;
	requires org.burningwave.json;
	requires org.burningwave.reflection;
	requires org.jsoup;

	opens org.rg.game.lottery.application to com.fasterxml.jackson.databind;

}
