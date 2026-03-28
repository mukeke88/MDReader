<template>
  <div class="control-group">
    <button class="secondary-button" @click="$emit('expand-all')">Expand All</button>
    <button class="secondary-button" @click="$emit('collapse-all')">Collapse All</button>
  </div>
</template>
