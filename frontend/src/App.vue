<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'

const API = '/api'                 // 走 Vite 代理 → 后端 8080
const devices = ref([])
const selectedId = ref(null)
const error = ref('')
const chartEl = ref(null)
let chart = null
let timer = null

async function loadDevices() {
  try {
    const res = await fetch(`${API}/devices`)
    if (!res.ok) throw new Error('HTTP ' + res.status)
    devices.value = await res.json()
    error.value = ''
    if (!selectedId.value && devices.value.length) {
      selectDevice(devices.value[0].device_id)
    }
  } catch (e) {
    error.value = '加载设备失败：' + e.message
  }
}

async function loadHistory(id) {
  try {
    const res = await fetch(`${API}/devices/${id}/history?hours=6`)
    if (!res.ok) throw new Error('HTTP ' + res.status)
    renderChart(id, await res.json())
  } catch (e) {
    error.value = '加载历史失败：' + e.message
  }
}

function renderChart(id, rows) {
  if (!chart) return
  const temp = rows.map(r => [new Date(r.ts).getTime(), r.temp])
  const humi = rows.map(r => [new Date(r.ts).getTime(), r.humi])
  chart.setOption({
    title: { text: `设备 ${id} · 温湿度（近 6 小时）`, left: 'center', textStyle: { fontSize: 14 } },
    tooltip: { trigger: 'axis' },
    legend: { data: ['温度 ℃', '湿度 %'], bottom: 0 },
    grid: { left: 48, right: 24, top: 48, bottom: 48 },
    xAxis: { type: 'time' },
    yAxis: { type: 'value' },
    series: [
      { name: '温度 ℃', type: 'line', showSymbol: false, smooth: true, data: temp },
      { name: '湿度 %', type: 'line', showSymbol: false, smooth: true, data: humi }
    ]
  }, true)
}

function selectDevice(id) {
  selectedId.value = id
  loadHistory(id)
}

const fmt = v => (v === undefined || v === null || v === 'NULL' ? '—' : v)

onMounted(async () => {
  await nextTick()
  chart = echarts.init(chartEl.value)
  window.addEventListener('resize', () => chart && chart.resize())
  await loadDevices()
  timer = setInterval(loadDevices, 10000)   // 每 10s 刷新设备实时值
})

onUnmounted(() => {
  clearInterval(timer)
  chart && chart.dispose()
})
</script>

<template>
  <div class="page">
    <header class="top">
      <h1>🌾 智慧农业 IoT 看板</h1>
      <span class="sub">设备实时监测 · 每 10s 刷新</span>
    </header>

    <p v-if="error" class="err">{{ error }}</p>

    <section class="cards">
      <div
        v-for="d in devices"
        :key="d.device_id"
        :class="['card', { active: d.device_id === selectedId }]"
        @click="selectDevice(d.device_id)"
      >
        <div class="card-head">
          <span class="dot" :class="{ on: d.online }"></span>
          <b>{{ d.device_id }}</b>
          <span class="count">{{ d.msg_count }} 条</span>
        </div>
        <div class="metrics">
          <div><span>温度</span>{{ fmt(d.latest?.temp) }} ℃</div>
          <div><span>湿度</span>{{ fmt(d.latest?.humi) }} %</div>
          <div><span>光照</span>{{ fmt(d.latest?.light) }}</div>
          <div><span>PH</span>{{ fmt(d.latest?.soilPh) }}</div>
        </div>
        <div class="seen">最近上报：{{ d.last_seen || '—' }}</div>
      </div>
    </section>

    <section class="chart-wrap">
      <div ref="chartEl" class="chart"></div>
    </section>
  </div>
</template>

<style>
* { box-sizing: border-box; }
body { margin: 0; font-family: -apple-system, "PingFang SC", "Microsoft YaHei", sans-serif; background: #f5f7fa; color: #1f2d3d; }
.page { max-width: 1100px; margin: 0 auto; padding: 24px; }
.top { display: flex; align-items: baseline; gap: 12px; margin-bottom: 16px; }
.top h1 { font-size: 22px; margin: 0; }
.top .sub { color: #8a94a6; font-size: 13px; }
.err { background: #fef0f0; color: #f56c6c; padding: 8px 12px; border-radius: 6px; }
.cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 14px; margin-bottom: 20px; }
.card { background: #fff; border: 1px solid #e6e9f0; border-radius: 10px; padding: 14px; cursor: pointer; transition: .15s; }
.card:hover { box-shadow: 0 4px 14px rgba(0,0,0,.06); }
.card.active { border-color: #409eff; box-shadow: 0 0 0 2px rgba(64,158,255,.18); }
.card-head { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; }
.card-head b { font-size: 16px; }
.card-head .count { margin-left: auto; color: #8a94a6; font-size: 12px; }
.dot { width: 9px; height: 9px; border-radius: 50%; background: #c0c4cc; }
.dot.on { background: #67c23a; }
.metrics { display: grid; grid-template-columns: 1fr 1fr; gap: 6px 12px; font-size: 14px; }
.metrics span { display: inline-block; width: 38px; color: #8a94a6; }
.seen { margin-top: 10px; color: #b6bccb; font-size: 12px; }
.chart-wrap { background: #fff; border: 1px solid #e6e9f0; border-radius: 10px; padding: 12px; }
.chart { width: 100%; height: 360px; }
</style>
