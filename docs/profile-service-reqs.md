# Profile Service MVP - Essential Requirements Only

## 🎯 MVP Core Hypothesis
**"Users can create attractive profiles and discover potential matches based on basic criteria"**

## 1. MVP Functional Requirements (MUST HAVE)

### 👤 Basic Profile Management
| ID | Requirement | Why Essential | Complexity |
|---|---|---|---|
| **MVP-F01** | Create profile with name, age, bio, location | Core matching data | LOW |
| **MVP-F02** | Upload 1-5 photos to S3 | Visual attraction is primary driver | MEDIUM |
| **MVP-F03** | Edit profile text fields | Users need to iterate on their profile | LOW |
| **MVP-F04** | Set basic preferences (age range, max distance) | Essential for targeted matching | LOW |
| **MVP-F05** | Get profile by ID | Other services need profile data | LOW |

### 📷 Minimal Photo Management
| ID | Requirement | Why Essential | Complexity |
|---|---|---|---|
| **MVP-P01** | Upload JPEG/PNG photos to S3 | Core feature for dating | MEDIUM |
| **MVP-P02** | Display photos via signed URLs | Basic security | LOW |
| **MVP-P03** | Set one primary photo | Matching service needs main image | LOW |
| **MVP-P04** | Delete photos | User control over content | LOW |

### 🔍 Basic Discovery
| ID | Requirement | Why Essential | Complexity |
|---|---|---|---|
| **MVP-D01** | Get profiles within age range | Core matching criteria | MEDIUM |
| **MVP-D02** | Filter by maximum distance from user location | Geographic relevance | HIGH |
| **MVP-D03** | Exclude specific user IDs | Prevent showing already seen profiles | LOW |
| **MVP-D04** | Return max 20 profiles per request | Prevent performance issues | LOW |

## 2. MVP Non-Functional Requirements (REALISTIC)

### 🚀 Performance (Good Enough)
| ID | Requirement | MVP Target | Production Target |
|---|---|---|---|
| **MVP-N01** | Profile retrieval response time | < 500ms (95th percentile) | < 200ms |
| **MVP-N02** | Photo upload processing | < 10 seconds | < 5 seconds |
| **MVP-N03** | Concurrent users support | 500 concurrent users | 10,000 |
| **MVP-N04** | Discovery query performance | < 1 second | < 500ms |

### 🛡️ Security (Minimal Viable)
| ID | Requirement | Why Essential |
|---|---|---|
| **MVP-S01** | JWT token validation | Basic auth protection |
| **MVP-S02** | Users can only edit own profiles | Prevent data corruption |
| **MVP-S03** | Photo file size limit (10MB) | Prevent abuse/costs |
| **MVP-S04** | Basic input validation | Prevent XSS/injection |

### 📊 Reliability (Startup Level)
| ID | Requirement | MVP Target |
|---|---|---|
| **MVP-R01** | Service uptime | 99% (7.2 hours downtime/month) |
| **MVP-R02** | Database backup | Daily backups |
| **MVP-R03** | Basic error handling | Graceful failures with error messages |

## 3. MVP API Design (Simplified)

### Core Endpoints Only
```yaml
# Profile CRUD
POST   /api/v1/profiles                    # Create profile
GET    /api/v1/profiles/{id}               # Get profile  
PUT    /api/v1/profiles/{id}               # Update profile
DELETE /api/v1/profiles/{id}               # Soft delete

# Photo Management  
POST   /api/v1/profiles/{id}/photos        # Upload photo
DELETE /api/v1/profiles/{id}/photos/{photoId}  # Delete photo
PUT    /api/v1/profiles/{id}/photos/{photoId}/primary  # Set primary

# Discovery
GET    /api/v1/profiles/discover           # Get profiles for matching
```

### Simplified Profile Data Structure
```json
{
  "profileId": "uuid",
  "userId": "uuid", 
  "firstName": "string",
  "age": "number",
  "bio": "string",
  "city": "string",
  "photos": [
    {
      "photoId": "uuid",
      "url": "signed_s3_url", 
      "isPrimary": "boolean"
    }
  ],
  "preferences": {
    "minAge": "number",
    "maxAge": "number", 
    "maxDistance": "number"
  },
  "location": {
    "latitude": "number",
    "longitude": "number"
  }
}
```

## 4. MVP Database Schema (Minimal)

### Simplified Tables
```sql
-- Profiles table (essential fields only)
CREATE TABLE profiles (
  profile_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL UNIQUE,
  first_name VARCHAR(50) NOT NULL,
  age INTEGER NOT NULL CHECK (age >= 18 AND age <= 100),
  bio TEXT,
  city VARCHAR(100),
  latitude DECIMAL(10, 8),
  longitude DECIMAL(11, 8),
  min_age INTEGER DEFAULT 18,
  max_age INTEGER DEFAULT 100, 
  max_distance INTEGER DEFAULT 50,
  is_active BOOLEAN DEFAULT true,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Photos table (simplified)
CREATE TABLE profile_photos (
  photo_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  profile_id UUID NOT NULL REFERENCES profiles(profile_id) ON DELETE CASCADE,
  s3_key VARCHAR(255) NOT NULL,
  is_primary BOOLEAN DEFAULT false,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Essential indexes only
CREATE INDEX idx_profiles_user_id ON profiles(user_id);
CREATE INDEX idx_profiles_location ON profiles(latitude, longitude) WHERE is_active = true;
CREATE INDEX idx_profiles_age ON profiles(age) WHERE is_active = true;
```

## 5. What We're CUTTING from MVP

### ❌ Features to Build Later
| Feature | Why Cut | Add in Version |
|---|---|---|
| **Profile verification** | Complex process, not core | v2.0 |
| **Multiple photo ordering** | Nice UX, but not essential | v1.5 |
| **Rich preferences** (interests, lifestyle) | Complex matching logic | v2.0 |
| **Profile analytics** | Data insights, not core dating | v3.0 |
| **Social media integration** | Additional complexity | v2.0 |
| **Profile reporting/moderation** | Manual moderation for MVP | v1.5 |
| **Advanced location features** (travel mode) | Edge case optimization | v2.0 |
| **Profile completeness scoring** | Algorithm optimization | v2.0 |
| **Blocked users management** | Can handle manually for MVP | v1.5 |

### ❌ Technical Features to Simplify
| Feature | MVP Approach | Future Enhancement |
|---|---|---|
| **Photo thumbnails** | Use S3 on-demand resizing | Dedicated thumbnail generation |
| **Advanced caching** | Basic in-memory cache | Redis cluster |
| **Database sharding** | Single PostgreSQL instance | Multi-region sharding |
| **CDN for photos** | Direct S3 access | CloudFront distribution |
| **Real-time updates** | Poll-based refresh | WebSocket/Server-sent events |
| **Advanced monitoring** | Basic health checks | Full observability stack |

## 6. MVP Development Timeline (8-12 weeks)

### Week 1-2: Foundation
- [ ] Database schema setup
- [ ] Basic CRUD API endpoints
- [ ] JWT authentication middleware
- [ ] AWS S3 integration

### Week 3-4: Core Features  
- [ ] Profile creation/editing
- [ ] Photo upload/delete
- [ ] Basic validation and error handling

### Week 5-6: Discovery
- [ ] Location-based profile retrieval
- [ ] Age/distance filtering
- [ ] Basic pagination

### Week 7-8: Polish & Testing
- [ ] Integration testing
- [ ] Performance optimization
- [ ] Basic monitoring setup
- [ ] Documentation

### Week 9-10: Deployment
- [ ] Production environment setup
- [ ] CI/CD pipeline
- [ ] Basic monitoring alerts

### Week 11-12: Buffer & Launch Prep
- [ ] Bug fixes from testing
- [ ] Performance tuning
- [ ] Launch preparation

## 7. MVP Success Metrics

### 📊 Key Metrics to Track
| Metric | Target | Why Important |
|---|---|---|
| **Profile Creation Rate** | 80% of signups create profile | Core conversion |
| **Photo Upload Rate** | 90% of profiles have photos | Essential for matching |
| **Profile Completion Time** | < 5 minutes average | User experience |
| **Discovery API Usage** | > 50 requests per active user/day | Engagement indicator |
| **Profile Update Frequency** | 20% of users edit profile weekly | User investment |

### 🚨 Critical Issues to Monitor
- Profile creation failures > 5%
- Photo upload failures > 10%  
- Discovery API response time > 1 second
- Service downtime > 4 hours/month

## 8. MVP Post-Launch Priorities

### 🔥 Immediate Next Features (v1.1)
1. **Profile photo ordering** - Users want control over photo sequence
2. **Basic profile reporting** - Safety and abuse prevention  
3. **Improved error messages** - Better user experience
4. **Profile edit history** - Track what users change most

### 🎯 Version 2.0 Goals
1. **Profile verification system**
2. **Rich user preferences** (interests, lifestyle)
3. **Social media integration**
4. **Advanced matching algorithm inputs**

## 9. MVP Risk Mitigation

### 🚨 Biggest Risks
| Risk | Impact | Mitigation |
|---|---|---|
| **Location queries too slow** | Poor user experience | Pre-compute popular areas, add caching |
| **S3 costs too high** | Budget overrun | Image compression, storage lifecycle rules |
| **Database performance** | Service degradation | Query optimization, read replicas |
| **Photo abuse** | Content issues | Manual moderation queue, basic filters |

### 🛡️ Fallback Plans
- **Location search fails**: Fall back to city-based matching
- **S3 unavailable**: Graceful degradation without photos
- **Database slow**: Cached profile responses
- **High load**: Rate limiting and queue management

---

## 🎯 MVP Decision Framework

**Include in MVP if it answers YES to:**
1. Is this essential for users to find and match with each other?
2. Can the app function without this feature?
3. Can we validate our core hypothesis without this?
4. Is this feature simple enough to build reliably in 8-10 weeks?

**This gives us a dating app where users can:**
✅ Create attractive profiles with photos  
✅ Set basic preferences  
✅ Discover nearby potential matches  
✅ Let the matching service use this data  

**Everything else is optimization or enhancement for later versions.**

What do you think about this MVP scope? Too ambitious or too minimal?