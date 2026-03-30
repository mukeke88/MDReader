<template>
  <div class="floating-score">
    <span class="score-label">Score</span>
    <div class="score-values">
      <strong class="score-values__green">{{ greenScore }}</strong>
      <span class="score-values__divider">/</span>
      <strong class="score-values__red">{{ redScore }}</strong>
    </div>
    <p class="score-caption">Green / Red</p>
  </div>
</template>

<script setup>
defineProps({
  greenScore: {
    type: Number,
    required: true
  },
  redScore: {
    type: Number,
    required: true
  }
})
</script>
