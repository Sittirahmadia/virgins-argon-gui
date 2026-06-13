package dev.lvstrng.argon.module;

import dev.lvstrng.argon.utils.EncryptedString;

public enum Category {
	COMBAT(EncryptedString.of("Combat")),
	MOVEMENT(EncryptedString.of("Movement")),
	RENDER(EncryptedString.of("Render")),
	CLIENT(EncryptedString.of("Utility")),
	WORLD(EncryptedString.of("World")),
	MISC(EncryptedString.of("Misc"));
	public final CharSequence name;

	Category(CharSequence name) {
		this.name = name;
	}
}
