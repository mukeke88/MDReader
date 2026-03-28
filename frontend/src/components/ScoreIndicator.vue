<template>
  <div class="floating-score">
    <span class="score-label">Total Score</span>
    <strong>{{ score }}</strong>
  </div>
</template>

<script setup>
defineProps({
  score: {
    type: Number,
    required: true
  }
})
</script>
