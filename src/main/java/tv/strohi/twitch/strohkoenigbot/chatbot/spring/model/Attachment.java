package tv.strohi.twitch.strohkoenigbot.chatbot.spring.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

import java.io.*;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder(toBuilder = true)
@Log4j2
public class Attachment {
	public static Attachment withDefaultName(String content) {
		var now = LocalDateTime.now();
		var attachmentName = String.format("attachment-log_%04d-%02d-%02d_%02d-%02d-%02d-%09d.md", now.getYear(), now.getMonthValue(), now.getDayOfMonth(), now.getHour(), now.getMinute(), now.getSecond(), now.getNano());

		return Attachment.builder().name(attachmentName).stream(new ByteArrayInputStream(content.getBytes())).build();
	}

	public static Attachment fromFile(String path) {
		try {
			var file = new File(path);
			return Attachment.builder().name(file.getName()).stream(new FileInputStream(path)).build();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static Attachment fromStream(String name, InputStream stream) {
		return Attachment.builder().name(name).stream(stream).build();
	}

	private String name;
	private InputStream stream;

	public void close() {
		try {
			stream.close();
		} catch (Exception ex) {
			log.error("Could not close stream", ex);
		}
	}
}
