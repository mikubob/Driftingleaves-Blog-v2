<script setup>
import { formatCount } from '../format.js'
const props = defineProps({ page: Number, total: String, totalPages: String, loading: Boolean })
const emit = defineEmits(['change'])
</script>

<template>
  <div class="list-pager">
    <span>共 {{ formatCount(total) }} 条记录</span>
    <div class="pager-buttons">
      <el-button :disabled="loading || page <= 1" @click="emit('change', page - 1)">
        上一页
      </el-button>
      <span>第 {{ page }} / {{ totalPages === '0' ? '1' : totalPages }} 页</span>
      <el-button
        :disabled="loading || BigInt(page) >= BigInt(totalPages) || page >= 100000"
        @click="emit('change', page + 1)"
      >
        下一页
      </el-button>
    </div>
  </div>
</template>
