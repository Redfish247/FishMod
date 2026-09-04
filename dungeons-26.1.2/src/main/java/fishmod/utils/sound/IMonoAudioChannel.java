package fishmod.utils.sound;

/**
 * Duck interface implemented by ChannelMixin so SoundEngineMixin can re-centre every channel.
 * Kept OUT of the {@code fishmod.mixin} package — Mixin forbids direct references to non-mixin
 * classes that live in a declared mixin package.
 */
public interface IMonoAudioChannel {
    void fishmod$refreshPosition();
}
