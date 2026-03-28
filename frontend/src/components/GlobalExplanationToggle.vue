<template>
  <label class="floating-toggle">
    <span class="floating-toggle__label">Explanations</span>
    <button
      type="button"
      class="switch"
      :class="{ 'switch--on': modelValue }"
      :aria-pressed="modelValue"
      @click="$emit('update:modelValue', !modelValue)"
    >
      <span class="switch__text">{{ modelValue ? 'On' : 'Off' }}</span>
      <span class="switch__knob" />
    </button>
  </label>
</template>

<script setup>
defineProps({
  modelValue: {
    type: Boolean,
    required: true
  }
})

defineEmits(['update:modelValue'])
</script>
