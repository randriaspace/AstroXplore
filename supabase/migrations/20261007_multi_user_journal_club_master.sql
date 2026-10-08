-- Master Multi-User Journal Club Database Schema & RPC Functions
-- Handles Public vs Private clubs, Join Requests, Admin Approval, and Consensus Voting

-- 1. Ensure Groups Table Structure and missing columns
CREATE TABLE IF NOT EXISTS public.groups (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    display_id TEXT UNIQUE NOT NULL,
    name TEXT NOT NULL,
    description TEXT,
    owner_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    focus_area TEXT,
    is_public BOOLEAN DEFAULT TRUE,
    visibility TEXT DEFAULT 'public',
    status TEXT DEFAULT 'active',
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Safely alter table to add columns if groups table already existed
ALTER TABLE public.groups ADD COLUMN IF NOT EXISTS is_public BOOLEAN DEFAULT TRUE;
ALTER TABLE public.groups ADD COLUMN IF NOT EXISTS visibility TEXT DEFAULT 'public';

-- 2. Group Members Table
CREATE TABLE IF NOT EXISTS public.group_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID REFERENCES public.groups(id) ON DELETE CASCADE,
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    role TEXT DEFAULT 'member',
    joined_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(group_id, user_id)
);

-- 3. Group Join Requests Table
CREATE TABLE IF NOT EXISTS public.group_join_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID REFERENCES public.groups(id) ON DELETE CASCADE,
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    status TEXT DEFAULT 'pending',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(group_id, user_id)
);

-- 4. Group Papers Table
CREATE TABLE IF NOT EXISTS public.group_papers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID REFERENCES public.groups(id) ON DELETE CASCADE,
    bibcode TEXT NOT NULL,
    added_by UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    title TEXT,
    authors TEXT,
    added_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(group_id, bibcode)
);

-- 5. Paper Votes Table
CREATE TABLE IF NOT EXISTS public.group_paper_votes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID REFERENCES public.groups(id) ON DELETE CASCADE,
    bibcode TEXT NOT NULL,
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    voted_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(group_id, bibcode, user_id)
);

-- Helper 1: is_group_member
CREATE OR REPLACE FUNCTION public.is_group_member(p_group_id UUID)
RETURNS BOOLEAN
LANGUAGE SQL
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, public, auth
AS $$
    SELECT auth.uid() IS NOT NULL AND EXISTS (
        SELECT 1 FROM public.group_members AS gm
        WHERE gm.group_id = p_group_id AND gm.user_id = auth.uid()
    );
$$;

-- Helper 2: can_read_group
CREATE OR REPLACE FUNCTION public.can_read_group(p_group_id UUID)
RETURNS BOOLEAN
LANGUAGE SQL
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, public, auth
AS $$
    SELECT auth.uid() IS NOT NULL AND EXISTS (
        SELECT 1 FROM public.groups AS g
        WHERE g.id = p_group_id
          AND (
              COALESCE(g.is_public, TRUE) = TRUE
              OR COALESCE(g.visibility, 'public') = 'public'
              OR g.owner_id = auth.uid()
              OR public.is_group_member(g.id)
          )
    );
$$;

-- Drop previous overloaded functions to avoid return-type mismatch errors
DROP FUNCTION IF EXISTS public.submit_group_join_request(UUID) CASCADE;
DROP FUNCTION IF EXISTS public.approve_group_join_request(UUID) CASCADE;
DROP FUNCTION IF EXISTS public.reject_group_join_request(UUID) CASCADE;
DROP FUNCTION IF EXISTS public.decline_group_join_request(UUID) CASCADE;
DROP FUNCTION IF EXISTS public.toggle_paper_vote(UUID, TEXT) CASCADE;

-- RPC 1: Submit Join Request
CREATE OR REPLACE FUNCTION public.submit_group_join_request(p_group_id UUID)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_user_id UUID := auth.uid();
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'User must be authenticated.';
    END IF;

    -- Check if already a member
    IF EXISTS (SELECT 1 FROM public.group_members WHERE group_id = p_group_id AND user_id = v_user_id) THEN
        RETURN TRUE;
    END IF;

    INSERT INTO public.group_join_requests (group_id, user_id, status)
    VALUES (p_group_id, v_user_id, 'pending')
    ON CONFLICT (group_id, user_id) DO UPDATE SET status = 'pending';

    RETURN TRUE;
END;
$$;

-- RPC 2: Approve Join Request
CREATE OR REPLACE FUNCTION public.approve_group_join_request(p_request_id UUID)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_group_id UUID;
    v_user_id UUID;
    v_admin_id UUID := auth.uid();
BEGIN
    SELECT group_id, user_id INTO v_group_id, v_user_id
    FROM public.group_join_requests
    WHERE id = p_request_id;

    IF v_group_id IS NULL THEN
        RAISE EXCEPTION 'Join request not found.';
    END IF;

    -- Verify caller is admin or owner
    IF NOT EXISTS (
        SELECT 1 FROM public.group_members
        WHERE group_id = v_group_id AND user_id = v_admin_id AND role IN ('admin', 'owner')
    ) AND NOT EXISTS (
        SELECT 1 FROM public.groups WHERE id = v_group_id AND owner_id = v_admin_id
    ) THEN
        RAISE EXCEPTION 'Unauthorized: Only club admins can approve requests.';
    END IF;

    -- Update request status
    UPDATE public.group_join_requests SET status = 'accepted' WHERE id = p_request_id;

    -- Add to group members
    INSERT INTO public.group_members (group_id, user_id, role)
    VALUES (v_group_id, v_user_id, 'member')
    ON CONFLICT (group_id, user_id) DO NOTHING;

    RETURN TRUE;
END;
$$;

-- RPC 3: Reject Join Request
CREATE OR REPLACE FUNCTION public.reject_group_join_request(p_request_id UUID)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_group_id UUID;
    v_admin_id UUID := auth.uid();
BEGIN
    SELECT group_id INTO v_group_id
    FROM public.group_join_requests
    WHERE id = p_request_id;

    IF v_group_id IS NULL THEN
        RAISE EXCEPTION 'Join request not found.';
    END IF;

    -- Verify caller is admin or owner
    IF NOT EXISTS (
        SELECT 1 FROM public.group_members
        WHERE group_id = v_group_id AND user_id = v_admin_id AND role IN ('admin', 'owner')
    ) AND NOT EXISTS (
        SELECT 1 FROM public.groups WHERE id = v_group_id AND owner_id = v_admin_id
    ) THEN
        RAISE EXCEPTION 'Unauthorized.';
    END IF;

    UPDATE public.group_join_requests SET status = 'rejected' WHERE id = p_request_id;
    RETURN TRUE;
END;
$$;

-- RPC 4: Toggle Paper Vote
CREATE OR REPLACE FUNCTION public.toggle_paper_vote(p_group_id UUID, p_bibcode TEXT)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_user_id UUID := auth.uid();
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'User must be authenticated.';
    END IF;

    IF EXISTS (SELECT 1 FROM public.group_paper_votes WHERE group_id = p_group_id AND bibcode = p_bibcode AND user_id = v_user_id) THEN
        DELETE FROM public.group_paper_votes WHERE group_id = p_group_id AND bibcode = p_bibcode AND user_id = v_user_id;
        RETURN FALSE;
    ELSE
        INSERT INTO public.group_paper_votes (group_id, bibcode, user_id) VALUES (p_group_id, p_bibcode, v_user_id);
        RETURN TRUE;
    END IF;
END;
$$;

-- Enable Row Level Security (RLS)
ALTER TABLE public.groups ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.group_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.group_join_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.group_papers ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.group_paper_votes ENABLE ROW LEVEL SECURITY;

-- Grant permissions
GRANT ALL ON TABLE public.groups TO authenticated, service_role;
GRANT ALL ON TABLE public.group_members TO authenticated, service_role;
GRANT ALL ON TABLE public.group_join_requests TO authenticated, service_role;
GRANT ALL ON TABLE public.group_papers TO authenticated, service_role;
GRANT ALL ON TABLE public.group_paper_votes TO authenticated, service_role;

GRANT EXECUTE ON FUNCTION public.is_group_member TO authenticated, anon, service_role;
GRANT EXECUTE ON FUNCTION public.can_read_group TO authenticated, anon, service_role;
GRANT EXECUTE ON FUNCTION public.submit_group_join_request TO authenticated, anon, service_role;
GRANT EXECUTE ON FUNCTION public.approve_group_join_request TO authenticated, anon, service_role;
GRANT EXECUTE ON FUNCTION public.reject_group_join_request TO authenticated, anon, service_role;
GRANT EXECUTE ON FUNCTION public.toggle_paper_vote TO authenticated, anon, service_role;
