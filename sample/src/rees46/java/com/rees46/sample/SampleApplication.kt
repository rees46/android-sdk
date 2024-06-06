import com.personaclick.sdk.Personaclick
import com.personalizatio.sample.AbstractSampleApplication

class SampleApplication : AbstractSampleApplication<REES46>(REES46::class) {
    override val shopId: String
        get() = if (com.personalizatio.BuildConfig.DEBUG) "cb0516af5da25b1b41490072e679bc" else "357382bf66ac0ce2f1722677c59511";

    override fun initialize() {
        REES46.initialize(applicationContext, shopId)
    }
}
