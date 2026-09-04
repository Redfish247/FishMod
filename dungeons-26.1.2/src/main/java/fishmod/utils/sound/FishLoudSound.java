package fishmod.utils.sound;

/**
 * Marker for a sound instance whose gain is allowed to exceed Minecraft's normal 1.0 ceiling.
 * {@link fishmod.mixin.SoundEngineMixin} multiplies the computed volume by {@link #fishmod$boost()}
 * and {@link fishmod.mixin.ChannelMixin} raises the OpenAL source's AL_MAX_GAIN so the driver
 * doesn't re-clamp it. Used for the ">100%" feature-cue volume sliders.
 */
public interface FishLoudSound {
    float fishmod$boost();
}
