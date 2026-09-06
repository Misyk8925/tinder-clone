package com.tinder.clone.moderation.application.policy

interface PolicyStateStore {
    fun loadPolicies(): List<PolicyDocument>
    fun loadActivations(): List<PolicyActivation>
    fun insertPolicy(document: PolicyDocument, actor: String)
    fun updatePolicy(document: PolicyDocument, expectedVersion: Long, actor: String)
    fun upsertActivation(activation: PolicyActivation, expectedVersion: Long?, actor: String)
}

class InMemoryPolicyStateStore : PolicyStateStore {
    private val policies = linkedMapOf<String, PolicyDocument>()
    private val activations = linkedMapOf<Pair<com.tinder.clone.moderation.domain.model.ContentType?, String?>, PolicyActivation>()

    @Synchronized override fun loadPolicies() = policies.values.toList()
    @Synchronized override fun loadActivations() = activations.values.toList()

    @Synchronized
    override fun insertPolicy(document: PolicyDocument, actor: String) {
        if (policies.putIfAbsent(document.version, document) != null) {
            throw PolicyLifecycleException("POLICY_VERSION_EXISTS", "Policy version already exists")
        }
    }

    @Synchronized
    override fun updatePolicy(document: PolicyDocument, expectedVersion: Long, actor: String) {
        val current = policies[document.version] ?: throw PolicyLifecycleException("NOT_FOUND", "Policy not found")
        if (current.aggregateVersion != expectedVersion) throw PolicyLifecycleException("VERSION_CONFLICT", "Resource changed")
        policies[document.version] = document
    }

    @Synchronized
    override fun upsertActivation(activation: PolicyActivation, expectedVersion: Long?, actor: String) {
        val key = activation.contentType to activation.locale?.lowercase()
        val current = activations[key]
        if (expectedVersion != null && current?.aggregateVersion != expectedVersion) {
            throw PolicyLifecycleException("VERSION_CONFLICT", "Resource changed")
        }
        activations[key] = activation
    }
}
