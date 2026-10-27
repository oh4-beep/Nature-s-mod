package com.naturesmod.disaster;

import java.util.ArrayList;
import java.util.List;

import com.naturesmod.NaturesMod;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * What the server tells clients about disasters: every few ticks how each one stands (where it is, how
 * strong, which way it moves), and the moments that happen once (a fireball's flight, an impact, a
 * blast, a bomb thrown from a volcano, a siren), which the clients draw and sound out themselves.
 */
public final class DisasterNet {
	private DisasterNet() {
	}

	/** A disaster as it stands: the generic part every kind has and a few numbers of its own (see each kind). */
	public record State(int id, int type, double x, double y, double z, float radius, float intensity, float strength, float dirX,
			float dirZ, int age, int duration, float[] extra) {
		void write(FriendlyByteBuf buf) {
			buf.writeVarInt(id);
			buf.writeByte(type);
			buf.writeDouble(x);
			buf.writeDouble(y);
			buf.writeDouble(z);
			buf.writeFloat(radius);
			buf.writeFloat(intensity);
			buf.writeFloat(strength);
			buf.writeFloat(dirX);
			buf.writeFloat(dirZ);
			buf.writeVarInt(age);
			buf.writeVarInt(duration);
			writeFloats(buf, extra);
		}

		static State read(FriendlyByteBuf buf) {
			return new State(buf.readVarInt(), buf.readByte(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(),
					buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readVarInt(), readFloats(buf));
		}

		public DisasterType kind() {
			return DisasterType.values()[type];
		}

		public float extra(int i) {
			return i < extra.length ? extra[i] : 0.0F;
		}
	}

	/** Every disaster in the player's dimension. */
	public record Sync(List<State> states) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<Sync> TYPE = new CustomPacketPayload.Type<>(NaturesMod.id("disaster_sync"));
		public static final StreamCodec<FriendlyByteBuf, Sync> CODEC = CustomPacketPayload.codec(Sync::write, Sync::new);

		private Sync(FriendlyByteBuf buf) {
			this(readStates(buf));
		}

		private void write(FriendlyByteBuf buf) {
			buf.writeVarInt(states.size());
			for (State state : states) {
				state.write(buf);
			}
		}

		private static List<State> readStates(FriendlyByteBuf buf) {
			int count = buf.readVarInt();
			List<State> out = new ArrayList<>(count);
			for (int i = 0; i < count; i++) {
				out.add(State.read(buf));
			}
			return out;
		}

		@Override
		public CustomPacketPayload.Type<Sync> type() {
			return TYPE;
		}
	}

	/** Something that happens once in a disaster, at a place, with numbers that depend on its kind. */
	public record Event(int kind, double x, double y, double z, float[] data) implements CustomPacketPayload {
		/** A fireball crossing the sky: velocity (3), size, fate (0 burns up, 1 bursts, 2 strikes), flight ticks. */
		public static final int METEOR = 0;
		/** A meteor striking the ground: crater radius. */
		public static final int IMPACT = 1;
		/** A meteor bursting in the air: size. */
		public static final int AIRBURST = 2;
		/** A volcano's great blast: index of explosivity. */
		public static final int BLAST = 3;
		/** A volcanic bomb thrown from the vent: velocity (3), size. */
		public static final int BOMB = 4;
		/** The warning sirens of a town, far off. */
		public static final int SIREN = 5;
		/** A volcanic bomb landing: size. */
		public static final int BOMB_LANDS = 6;

		public static final CustomPacketPayload.Type<Event> TYPE = new CustomPacketPayload.Type<>(NaturesMod.id("disaster_event"));
		public static final StreamCodec<FriendlyByteBuf, Event> CODEC = CustomPacketPayload.codec(Event::write, Event::new);

		private Event(FriendlyByteBuf buf) {
			this(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), readFloats(buf));
		}

		private void write(FriendlyByteBuf buf) {
			buf.writeVarInt(kind);
			buf.writeDouble(x);
			buf.writeDouble(y);
			buf.writeDouble(z);
			writeFloats(buf, data);
		}

		public float data(int i) {
			return i < data.length ? data[i] : 0.0F;
		}

		@Override
		public CustomPacketPayload.Type<Event> type() {
			return TYPE;
		}
	}

	/**
	 * A push on the player it is sent to. A player's own game moves the player, so this is how a disaster moves one:
	 * set to a velocity (carried off), given a kick (a blast), or pulled toward the speed of the air at a rate (the
	 * wind), which the player's game works out from how fast the player is really going. At most `max` blocks a tick
	 * after it (none when 0).
	 */
	public record Push(int mode, float x, float y, float z, float rate, float lift, float max) implements CustomPacketPayload {
		public static final int SET = 0;
		public static final int ADD = 1;
		/** Toward (x, y, z): `rate` of the way across, and up (off the ground only) `lift` of the way when the air rises faster. */
		public static final int TOWARD = 2;

		public static final CustomPacketPayload.Type<Push> TYPE = new CustomPacketPayload.Type<>(NaturesMod.id("disaster_push"));
		public static final StreamCodec<FriendlyByteBuf, Push> CODEC = CustomPacketPayload.codec(Push::write, Push::new);

		private Push(FriendlyByteBuf buf) {
			this(buf.readByte(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat());
		}

		private void write(FriendlyByteBuf buf) {
			buf.writeByte(mode);
			buf.writeFloat(x);
			buf.writeFloat(y);
			buf.writeFloat(z);
			buf.writeFloat(rate);
			buf.writeFloat(lift);
			buf.writeFloat(max);
		}

		@Override
		public CustomPacketPayload.Type<Push> type() {
			return TYPE;
		}
	}

	/** How many pushes this game has been given (counted by the client, for the tests to see). */
	public static int pushesReceived;

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Sync.TYPE, Sync.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Event.TYPE, Event.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Push.TYPE, Push.CODEC);
	}

	/** Pushes a player (see {@link Push}); false if its game can't hear of it this way. */
	static boolean push(ServerPlayer player, Push push) {
		if (!ServerPlayNetworking.canSend(player, Push.TYPE)) {
			return false;
		}
		ServerPlayNetworking.send(player, push);
		return true;
	}

	/** Sends an event to the players of a level within `range` blocks of it (every one of them when range <= 0). */
	public static void send(ServerLevel level, Event event, double range) {
		for (ServerPlayer player : level.players()) {
			double dx = player.getX() - event.x();
			double dz = player.getZ() - event.z();
			if ((range <= 0 || dx * dx + dz * dz <= range * range) && ServerPlayNetworking.canSend(player, Event.TYPE)) {
				ServerPlayNetworking.send(player, event);
			}
		}
	}

	static void writeFloats(FriendlyByteBuf buf, float[] values) {
		buf.writeVarInt(values.length);
		for (float value : values) {
			buf.writeFloat(value);
		}
	}

	static float[] readFloats(FriendlyByteBuf buf) {
		int count = buf.readVarInt();
		if (count < 0 || count > 4096) {
			throw new IllegalArgumentException("bad disaster data length " + count);
		}
		float[] out = new float[count];
		for (int i = 0; i < count; i++) {
			out[i] = buf.readFloat();
		}
		return out;
	}
}
