package com.personaclick.sample

import com.personaclick.sdk.Personaclick
import com.personalization.sample.AbstractMainActivity

class MainActivity : AbstractMainActivity<Personaclick>(Personaclick.getInstance())
