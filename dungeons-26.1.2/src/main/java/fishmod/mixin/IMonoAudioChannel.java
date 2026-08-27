package fishmod.mixin;

/** Implemented by {@link ChannelMixin} so {@link SoundEngineMixin} can re-centre every channel. */
public interface IMonoAudioChannel {
    void fishmod$refreshPosition();
}
