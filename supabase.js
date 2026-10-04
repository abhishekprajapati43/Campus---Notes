// import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

// const supabaseUrl = "https://tauxcecmmuwiugypcxsy.supabase.co";

// const supabaseKey = "sb_publishable_LLqh8M02X-2hGixiKlml7Q_2QuUXAIc";

// export const supabase = createClient(
//     supabaseUrl,
//     supabaseKey
// );


// =====================================================
// TechCampus_Hub
// supabase.js
// =====================================================

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";


// -----------------------------------------------------
// Supabase Project Configuration
// -----------------------------------------------------

const supabaseUrl = "https://tauxcecmmuwiugypcxsy.supabase.co";

const supabaseKey =
    "sb_publishable_LLqh8M02X-2hGixiKlml7Q_2QuUXAIc";


// -----------------------------------------------------
// Supabase Client
// -----------------------------------------------------

export const supabase = createClient(
    supabaseUrl,
    supabaseKey
);