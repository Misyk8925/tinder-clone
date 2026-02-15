#!/bin/bash

# Quick Performance Test - Run After Optimization

echo "🚀 Swipes Service Performance Test"
echo "===================================="
echo ""

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${YELLOW}Step 1: Restart service with new configuration${NC}"
echo "cd services/swipes && mvn spring-boot:run"
echo ""
echo "Press ENTER when service is running..."
read

echo ""
echo -e "${YELLOW}Step 2: Running baseline test (500 connections)${NC}"
echo ""

cd "$(dirname "$0")"
./simple-load-test.sh

echo ""
echo -e "${GREEN}✅ Test complete!${NC}"
echo ""
echo "📊 What to check:"
echo "  1. RPS should be 8,000-12,000+ (was 666 before, target 10k+)"
echo "  2. Latency should be 30-50ms (was 551ms before)"
echo "  3. No timeouts or socket errors (was 440 timeouts before)"
echo ""
echo "🔥 Key improvements made:"
echo "  - Removed @Transactional blocking from request path"
echo "  - Changed Kafka acks=0 (fire-and-forget)"
echo "  - Enabled thread-bound Kafka producers for higher producer parallelism"
echo "  - Reduced logging overhead"
echo ""
echo "📝 Check Kafka lag:"
echo "  kafka-consumer-groups --bootstrap-server localhost:9092 --group swipes-group --describe"
echo ""
echo "🔍 Monitor database:"
echo "  psql -h 127.0.0.1 -p 54322 -U postgres -c \"SELECT count(*) FROM pg_stat_activity WHERE datname='postgres';\""
echo ""
echo "📈 For detailed analysis, see:"
echo "  - CRITICAL_FIXES.md (explains all changes)"
echo "  - PERFORMANCE_OPTIMIZATION.md (detailed tuning)"
echo "  - LOAD_TEST_README.md (full test guide)"
