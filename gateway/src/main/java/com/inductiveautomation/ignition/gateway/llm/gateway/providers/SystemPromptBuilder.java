package com.inductiveautomation.ignition.gateway.llm.gateway.providers;

import com.inductiveautomation.ignition.gateway.llm.gateway.auth.AuthContext;
import com.inductiveautomation.ignition.gateway.llm.gateway.auth.Permission;
import com.inductiveautomation.ignition.gateway.llm.gateway.policy.EnvironmentMode;

import java.util.Set;

/**
 * Builds context-aware system prompts for LLM interactions.
 * The system prompt instructs the LLM on how to behave and what capabilities it has.
 */
public class SystemPromptBuilder {

    private static final String BASE_PROMPT =
            "You are an AI assistant integrated with an Ignition Gateway. You can help users " +
            "manage Ignition resources including tags, Perspective views, scripts, and named queries.\n\n" +
            "CRITICAL: TAG PATH FORMAT\n" +
            "Tag paths use this format: [provider]path/to/tag\n" +
            "The provider name goes INSIDE square brackets at the START of the path.\n\n" +
            "CORRECT examples:\n" +
            "- [default] - Root of the default provider\n" +
            "- [default]Folder/SubFolder/TagName - A tag in the default provider\n" +
            "- [Sample_Tags] - Root of the Sample_Tags provider (NOT [default]Sample_Tags)\n" +
            "- [Sample_Tags]Realistic/Realistic0 - A tag in Sample_Tags provider\n" +
            "- [System]Gateway/CurrentDateTime - A system tag\n\n" +
            "WRONG examples (DO NOT DO THIS):\n" +
            "- [default]Sample_Tags/path - WRONG! Sample_Tags is a provider, not a folder\n" +
            "- Sample_Tags/path - WRONG! Missing provider brackets\n\n" +
            "LISTING TAG PROVIDERS:\n" +
            "To list all tag providers, use list_tags with parentPath: \"*\" (asterisk)\n" +
            "This returns all available providers like: default, Sample_Tags, System\n\n" +
            "IMPORTANT GUIDELINES:\n" +
            "1. Always confirm destructive actions (delete, overwrite) before executing\n" +
            "2. Use dryRun=true first for complex changes to preview the result\n" +
            "3. Provide clear explanations of what each action will do\n" +
            "4. If a request is ambiguous, ask for clarification\n" +
            "5. Respect the user's permission level - don't attempt actions they can't perform\n" +
            "6. Always include the full tag path with provider (e.g., '[default]Folder/Tag')\n\n";

    private static final String PRODUCTION_WARNING =
            "WARNING: You are connected to a PRODUCTION system. Exercise extra caution:\n" +
            "- Always use dryRun=true before making changes\n" +
            "- Confirm all modifications with the user before executing\n" +
            "- Prefer read operations over writes when gathering information\n" +
            "- Double-check paths and values before any write operation\n\n";

    private static final String DRY_RUN_ONLY_WARNING =
            "NOTE: Your API key only allows dry-run operations. All write operations will be " +
            "validated but not executed. You can show the user what would happen.\n\n";

    /**
     * Builds the system prompt based on auth context and environment.
     */
    public static String buildSystemPrompt(AuthContext auth, EnvironmentMode envMode) {
        StringBuilder prompt = new StringBuilder();

        // Base instructions
        prompt.append(BASE_PROMPT);

        // Environment context
        prompt.append("CURRENT ENVIRONMENT: ").append(envMode.name()).append("\n");

        if (envMode == EnvironmentMode.PRODUCTION) {
            prompt.append(PRODUCTION_WARNING);
        } else if (envMode == EnvironmentMode.TEST) {
            prompt.append("This is a TEST environment. Operations will affect test resources only.\n\n");
        } else {
            prompt.append("This is a DEVELOPMENT environment. You can experiment freely.\n\n");
        }

        // Dry-run only restriction
        if (auth.isDryRunOnly()) {
            prompt.append(DRY_RUN_ONLY_WARNING);
        }

        // Permission context
        prompt.append("YOUR AVAILABLE ACTIONS:\n");
        Set<Permission> permissions = auth.getPermissions();

        if (permissions.contains(Permission.ADMIN)) {
            prompt.append("- Full administrative access (all operations available)\n");
        } else {
            for (Permission perm : permissions) {
                if (perm != Permission.DRY_RUN_ONLY) {
                    prompt.append("- ").append(describePermission(perm)).append("\n");
                }
            }
        }

        // Resource context
        prompt.append("\nAVAILABLE RESOURCES:\n");
        prompt.append("- Tag providers: Use list_tags to discover available providers\n");
        prompt.append("- Projects: Use list_projects to discover available projects\n");
        prompt.append("- Views: Use list_views with a project name to see available views\n");

        // Usage hints
        prompt.append("\nUSAGE HINTS:\n");
        prompt.append("- Tag paths always include the provider: [default]FolderName/TagName\n");
        prompt.append("- View paths are: ProjectName/Path/To/View\n");
        prompt.append("- Use dryRun=true to preview changes before applying\n");
        prompt.append("- For delete operations, set force=true to confirm after warning the user\n");

        return prompt.toString();
    }

    /**
     * Builds a minimal system prompt for simple operations.
     */
    public static String buildMinimalPrompt(AuthContext auth) {
        return "You are an Ignition Gateway assistant. " +
                "Use the available tools to help manage tags and views. " +
                "Always confirm destructive operations before executing.";
    }

    /**
     * Describes what a permission allows.
     */
    private static String describePermission(Permission perm) {
        if (perm == Permission.TAG_READ) {
            return "Read tag configurations and values";
        } else if (perm == Permission.TAG_CREATE) {
            return "Create new tags";
        } else if (perm == Permission.TAG_UPDATE) {
            return "Modify tag configurations";
        } else if (perm == Permission.TAG_DELETE) {
            return "Delete tags (requires confirmation)";
        } else if (perm == Permission.TAG_WRITE_VALUE) {
            return "Write values to existing tags";
        } else if (perm == Permission.VIEW_READ) {
            return "Read Perspective view definitions";
        } else if (perm == Permission.VIEW_CREATE) {
            return "Create new Perspective views";
        } else if (perm == Permission.VIEW_UPDATE) {
            return "Modify Perspective views";
        } else if (perm == Permission.VIEW_DELETE) {
            return "Delete Perspective views (requires confirmation)";
        } else if (perm == Permission.SCRIPT_READ) {
            return "Read script contents";
        } else if (perm == Permission.SCRIPT_CREATE) {
            return "Create new scripts";
        } else if (perm == Permission.SCRIPT_UPDATE) {
            return "Modify scripts";
        } else if (perm == Permission.SCRIPT_DELETE) {
            return "Delete scripts (requires confirmation)";
        } else if (perm == Permission.SCRIPT_EXECUTE) {
            return "Execute scripts (use with extreme caution)";
        } else if (perm == Permission.PROJECT_READ) {
            return "Read project configurations";
        } else if (perm == Permission.NAMED_QUERY_READ) {
            return "Read named query configurations";
        } else if (perm == Permission.NAMED_QUERY_EXECUTE) {
            return "Execute named queries";
        } else if (perm == Permission.READ_ALL) {
            return "Read access to all resources";
        } else if (perm == Permission.ADMIN) {
            return "Full administrative access";
        } else {
            return perm.getDescription();
        }
    }

    /**
     * Builds a prompt extension for specific resource context.
     */
    public static String buildResourceContextPrompt(String currentProject, String currentPath) {
        StringBuilder prompt = new StringBuilder();

        if (currentProject != null) {
            prompt.append("\nCURRENT CONTEXT:\n");
            prompt.append("- Working in project: ").append(currentProject).append("\n");

            if (currentPath != null) {
                prompt.append("- Current path: ").append(currentPath).append("\n");
            }
        }

        return prompt.toString();
    }
}
