package protocolsupport.protocol.pipeline.version.v_pe;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import net.md_5.bungee.protocol.DefinedPacket;
import net.md_5.bungee.protocol.packet.Chat;
import net.md_5.bungee.protocol.packet.Kick;
import net.md_5.bungee.protocol.packet.Login;
import net.md_5.bungee.protocol.packet.LoginSuccess;
import net.md_5.bungee.protocol.packet.PluginMessage;
import net.md_5.bungee.protocol.packet.Respawn;
import net.md_5.bungee.protocol.packet.StatusResponse;

import protocolsupport.api.Connection;
import protocolsupport.protocol.packet.middleimpl.writeable.NoopWriteablePacket;
import protocolsupport.protocol.packet.middleimpl.writeable.login.v_pe.LoginSuccessPacket;
import protocolsupport.protocol.packet.middleimpl.writeable.play.v_pe.KickPacket;
import protocolsupport.protocol.packet.middleimpl.writeable.play.v_pe.RespawnPacket;
import protocolsupport.protocol.packet.middleimpl.writeable.play.v_pe.StartGamePacket;
import protocolsupport.protocol.packet.middleimpl.writeable.play.v_pe.ToClientChatPacket;
import protocolsupport.protocol.packet.middleimpl.writeable.play.v_pe.CustomEventPacket;
import protocolsupport.protocol.packet.middleimpl.writeable.status.v_pe.StatusResponsePacket;
import protocolsupport.protocol.pipeline.version.AbstractPacketEncoder;
import protocolsupport.protocol.storage.NetworkDataCache;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;

public class ToClientPacketEncoder extends AbstractPacketEncoder {

	{
		registry.register(LoginSuccess.class, LoginSuccessPacket.class);
		registry.register(Login.class, StartGamePacket.class);
		registry.register(StatusResponse.class, StatusResponsePacket.class);
		registry.register(Kick.class, KickPacket.class);
		registry.register(Respawn.class, RespawnPacket.class);
		registry.register(Chat.class, ToClientChatPacket.class);
		//registry.register(PlayerListItem.class, NoopWriteablePacket.class); //TODO: implement it
		registry.register(PluginMessage.class, CustomEventPacket.class);
		registry.register(DefinedPacket.class, NoopWriteablePacket.class); //default
	}

	public ToClientPacketEncoder(Connection connection, NetworkDataCache cache) {
		super(connection, cache);
	}

	protected ArrayList<Pair<Object, ChannelPromise>> packetCache = new ArrayList<>(128);

	@Override
	public void write(final ChannelHandlerContext ctx, final Object msgObject, final ChannelPromise promise) throws Exception {
		if (acceptOutboundMessage(msgObject)) {
			DefinedPacket msg = (DefinedPacket) msgObject;
			if (msg instanceof PluginMessage && cache.isStashingClientPackets() && ((PluginMessage) msg).getTag().equals("ps:bungeeunlock")) {
				cache.setStashingClientPackets(false);
				//copy list so we can safely recurse back into this method
				ArrayList<Pair<Object, ChannelPromise>> packetCacheCopy = new ArrayList(packetCache);
				packetCache.clear();
				packetCache.trimToSize();
				for (Pair<Object, ChannelPromise> cachedPacket : packetCacheCopy) {
					write(ctx, cachedPacket.getLeft(), cachedPacket.getRight());
				}
				return;
			}
			// check if this is the bungee initiated chunk-cache-clearing dim switch
			if (msg instanceof Respawn && !cache.isStashingClientPackets()) {
				cache.setStashingClientPackets(true);
				super.write(ctx, msgObject, promise);
				return;
			}
		}
		if (cache.isStashingClientPackets()) {
			packetCache.add(new ImmutablePair(msgObject, promise));
		} else {
			super.write(ctx, msgObject, promise);
		}
	}

}
