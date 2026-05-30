local token_file = os.getenv("JWT_TOKENS_FILE")
local single_token = os.getenv("JWT_TOKEN")
local deck_limit = tonumber(os.getenv("DECK_LIMIT") or "20")
local deck_pages = tonumber(os.getenv("DECK_PAGES") or "1")
local fetch_me = os.getenv("FETCH_ME") ~= "0"

local tokens = {}
local counter = 0
local token_offset = 0
local token_stride = tonumber(os.getenv("WRK_THREADS") or "1")
local status_counts = {}
local deck_count = 0
local me_count = 0
local threads = {}

local function trim(value)
  return (value:gsub("^%s+", ""):gsub("%s+$", ""))
end

local function add_token(value)
  if value == nil then
    return
  end
  value = trim(value)
  if value ~= "" then
    table.insert(tokens, value)
  end
end

if single_token ~= nil and single_token ~= "" then
  add_token(single_token)
end

if token_file ~= nil and token_file ~= "" then
  local file = io.open(token_file, "r")
  if file == nil then
    error("JWT_TOKENS_FILE cannot be opened: " .. token_file)
  end

  for line in file:lines() do
    if line:sub(1, 1) ~= "#" then
      add_token(line)
    end
  end
  file:close()
end

if #tokens == 0 then
  error("Provide JWT_TOKEN or JWT_TOKENS_FILE")
end

local requests = {}
if fetch_me then
  table.insert(requests, { path = "/api/v1/profiles/me", kind = "me" })
end

for page = 0, deck_pages - 1 do
  local offset = page * deck_limit
  table.insert(requests, {
    path = "/api/v1/profiles/deck?offset=" .. offset .. "&limit=" .. deck_limit,
    kind = "deck"
  })
end

function setup(thread)
  local thread_index = #threads
  thread:set("token_offset", thread_index)
  table.insert(threads, thread)
end

function init(args)
  token_offset = tonumber(wrk.thread:get("token_offset")) or 0
end

request = function()
  counter = counter + 1
  local token_index = (token_offset + ((counter - 1) * token_stride)) % #tokens
  local token = tokens[token_index + 1]
  local next_request = requests[((counter - 1) % #requests) + 1]

  if next_request.kind == "deck" then
    deck_count = deck_count + 1
  else
    me_count = me_count + 1
  end

  return wrk.format("GET", next_request.path, {
    ["Accept"] = "application/json",
    ["Authorization"] = "Bearer " .. token
  })
end

response = function(status)
  status_counts[status] = (status_counts[status] or 0) + 1
  wrk.thread:set("status_" .. tostring(status), status_counts[status])
  wrk.thread:set("me_count", me_count)
  wrk.thread:set("deck_count", deck_count)
end

done = function(summary, latency, requests_done)
  local aggregated_status_counts = {}
  local aggregated_me_count = 0
  local aggregated_deck_count = 0

  for _, thread in ipairs(threads) do
    aggregated_me_count = aggregated_me_count + (tonumber(thread:get("me_count")) or 0)
    aggregated_deck_count = aggregated_deck_count + (tonumber(thread:get("deck_count")) or 0)

    for status = 100, 599 do
      local count = tonumber(thread:get("status_" .. tostring(status))) or 0
      if count > 0 then
        aggregated_status_counts[status] = (aggregated_status_counts[status] or 0) + count
      end
    end
  end

  io.write("\nprofile-deck wrk summary\n")
  io.write(string.format("tokens: %d\n", #tokens))
  io.write(string.format("me requests scheduled by script: %d\n", aggregated_me_count))
  io.write(string.format("deck requests scheduled by script: %d\n", aggregated_deck_count))
  io.write("status counts:\n")

  local statuses = {}
  for status, _ in pairs(aggregated_status_counts) do
    table.insert(statuses, status)
  end
  table.sort(statuses)

  for _, status in ipairs(statuses) do
    io.write(string.format("  %s: %d\n", status, aggregated_status_counts[status]))
  end
end
