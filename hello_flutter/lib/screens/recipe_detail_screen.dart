// ignore_for_file: slash_for_doc_comments

import 'package:flutter/material.dart';

import '../models/recipe_recommendation.dart';
import '../services/fridge_api_service.dart';
import '../theme/smart_fridge_tokens.dart';

/**
 * Recipe detail screen.
 * <p>
 * Entry screen for recipe details.
 * Receives one recipe and switches between desktop and mobile layouts.
 *
 * Official references:
 * StatefulWidget:
 * https://api.flutter.dev/flutter/widgets/StatefulWidget-class.html
 * State:
 * https://api.flutter.dev/flutter/widgets/State-class.html
 * Scaffold:
 * https://api.flutter.dev/flutter/material/Scaffold-class.html
 * SafeArea:
 * https://api.flutter.dev/flutter/widgets/SafeArea-class.html
 * LayoutBuilder:
 * https://api.flutter.dev/flutter/widgets/LayoutBuilder-class.html
 *
 * Open-source reference:
 * Flutter samples:
 * https://github.com/flutter/samples
 * material_3_demo:
 * https://github.com/flutter/samples/tree/main/material_3_demo
 */
class RecipeDetailScreen extends StatefulWidget {
  const RecipeDetailScreen({
    super.key,
    required this.recipe,
    this.useLiveApi = true,
    this.api,
  });

  final RecipeRecommendation recipe;
  final bool useLiveApi;
  final FridgeApiService? api;

  @override
  State<RecipeDetailScreen> createState() => _RecipeDetailScreenState();
}

/**
 * State for RecipeDetailScreen.
 * <p>
 * Stores local detail-page state such as the current recipe,
 * saved state, ingredient list, match score, and timing values.
 */
class _RecipeDetailScreenState extends State<RecipeDetailScreen> {
  bool _saved = false;
  late RecipeRecommendation _recipe;
  bool _loading = false;
  String? _detailError;
  bool _adding = false;
  bool _added = false;
  bool _addFailed = false;
  bool _addOutcomeUnknown = false;
  String? _groceryMessage;

  FridgeApiService get _api => widget.api ?? FridgeApiService.instance;

  @override
  void initState() {
    super.initState();
    _recipe = widget.recipe;
    if (widget.useLiveApi) _loadDetail();
  }

  @override
  void didUpdateWidget(covariant RecipeDetailScreen oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.recipe.id != widget.recipe.id ||
        oldWidget.useLiveApi != widget.useLiveApi) {
      _recipe = widget.recipe;
      _added = false;
      _addOutcomeUnknown = false;
      _groceryMessage = null;
      if (widget.useLiveApi) _loadDetail();
    }
  }

  Future<void> _loadDetail() async {
    setState(() {
      _loading = true;
      _detailError = null;
    });
    try {
      final recipe = await _api.fetchRecipeDetail(widget.recipe.id);
      if (!mounted) return;
      setState(() => _recipe = recipe);
    } catch (_) {
      if (!mounted) return;
      setState(
        () => _detailError = 'Could not load this recipe. Please try again.',
      );
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _addMissingIngredients() async {
    if (_adding || _added || _addOutcomeUnknown) return;
    setState(() {
      _adding = true;
      _addFailed = false;
      _groceryMessage = null;
    });
    try {
      final result = await _api.addMissingIngredientsToGrocery(_recipe.id);
      if (!mounted) return;
      setState(() {
        _added = true;
        _groceryMessage =
            '${result.addedCount} added, ${result.mergedCount} merged into your grocery list.';
      });
    } catch (error) {
      if (!mounted) return;
      final rejected =
          error is FridgeApiException &&
          const [400, 401, 403, 404, 409].contains(error.statusCode);
      setState(() {
        _addFailed = true;
        _addOutcomeUnknown = !rejected;
        _groceryMessage = !rejected
            ? 'We could not confirm whether ingredients were added. Check your grocery list before adding another batch.'
            : error.code == 'QUANTITY_LIMIT_EXCEEDED'
            ? 'A grocery quantity is too high. Reduce it before adding this recipe.'
            : 'Could not add ingredients. Please try again.';
      });
    } finally {
      if (mounted) setState(() => _adding = false);
    }
  }

  Future<void> _confirmAnotherBatch() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Add another batch?'),
        content: const Text(
          'The previous request may already have added these ingredients. '
          'Check your grocery list first. Adding again may double the quantities.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(dialogContext, true),
            child: const Text('Add another batch'),
          ),
        ],
      ),
    );
    if (!mounted || confirmed != true) return;
    setState(() => _addOutcomeUnknown = false);
    await _addMissingIngredients();
  }

  @override
  Widget build(BuildContext context) {
    if (widget.useLiveApi && (_loading || _detailError != null)) {
      return Scaffold(
        appBar: AppBar(title: const Text('Recipe details')),
        body: Center(
          child: _loading
              ? const CircularProgressIndicator()
              : Padding(
                  padding: const EdgeInsets.all(24),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Text(_detailError!, textAlign: TextAlign.center),
                      const SizedBox(height: 12),
                      FilledButton(
                        onPressed: _loadDetail,
                        child: const Text('Retry'),
                      ),
                    ],
                  ),
                ),
        ),
      );
    }
    final recipe = _recipe;
    final ingredients = _ingredientRows(recipe);
    final fromFridge = ingredients.where((e) => e.inFridge).length;
    final match = widget.useLiveApi
        ? recipe.matchPercent.clamp(0, 100).toInt()
        : ingredients.isEmpty
        ? 0
        : ((fromFridge / ingredients.length) * 100).round();
    final reviews = 124;
    final totalCook = recipe.prepMinutes + 5;

    return Scaffold(
      backgroundColor: SfColors.cream,
      bottomNavigationBar: widget.useLiveApi
          ? _groceryAction(ingredients)
          : null,
      body: SafeArea(
        child: LayoutBuilder(
          builder: (context, c) {
            if (c.maxWidth < 980) {
              return _MobileRecipeDetail(
                recipe: recipe,
                saved: _saved,
                onToggleSave: () => setState(() => _saved = !_saved),
                ingredients: ingredients,
                match: match,
              );
            }
            return Row(
              children: [
                _RecipeInfoRail(
                  recipe: recipe,
                  match: match,
                  ingredients: ingredients,
                  onBack: () => Navigator.of(context).pop(),
                  isLive: widget.useLiveApi,
                ),
                Expanded(
                  child: Column(
                    children: [
                      Expanded(
                        child: Container(
                          color: SfColors.whiteCard,
                          child: Stack(
                            children: [
                              Positioned(
                                top: 18,
                                left: 22,
                                child: widget.useLiveApi
                                    ? _TagPill(
                                        label: 'Rating ${recipe.rating}',
                                        fill: SfColors.chipBgLight,
                                      )
                                    : _ReviewPill(
                                        rating: recipe.rating,
                                        reviews: reviews,
                                      ),
                              ),
                              Positioned(
                                top: 14,
                                right: 18,
                                child: Row(
                                  children: [
                                    _CircleIconButton(
                                      icon: _saved
                                          ? Icons.bookmark
                                          : Icons.bookmark_border,
                                      onTap: () =>
                                          setState(() => _saved = !_saved),
                                    ),
                                    const SizedBox(width: 8),
                                    const _CircleIconButton(
                                      icon: Icons.share_outlined,
                                    ),
                                  ],
                                ),
                              ),
                              Positioned(
                                left: 18,
                                bottom: 16,
                                child: widget.useLiveApi
                                    ? _TagPill(
                                        label:
                                            '${recipe.prepMinutes} min cook time',
                                        fill: SfColors.chipBgLight,
                                      )
                                    : _TimelinePill(
                                        prep: recipe.prepMinutes,
                                        cook: 5,
                                        total: totalCook,
                                        serves: 2,
                                      ),
                              ),
                            ],
                          ),
                        ),
                      ),
                      SizedBox(
                        height: 290,
                        child: Row(
                          children: [
                            for (final item in ingredients)
                              Expanded(
                                child: _IngredientTile(
                                  item: item,
                                  kcal: widget.useLiveApi
                                      ? null
                                      : _kcalFor(item.name),
                                ),
                              ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            );
          },
        ),
      ),
    );
  }

  Widget _groceryAction(List<_IngredientItem> ingredients) {
    final hasMissing = ingredients.any((item) => !item.inFridge);
    final label = _adding
        ? 'Adding ingredients…'
        : _added
        ? 'Added to grocery list'
        : _addOutcomeUnknown
        ? 'Result unconfirmed'
        : ingredients.isEmpty
        ? 'No required ingredients listed'
        : !hasMissing
        ? 'All required ingredients are in your fridge'
        : 'Add missing ingredients';
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 12),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (_groceryMessage != null) ...[
              Text(
                _groceryMessage!,
                textAlign: TextAlign.center,
                style: TextStyle(
                  color: _addFailed ? Colors.red.shade700 : SfColors.matchGreen,
                ),
              ),
              const SizedBox(height: 8),
            ],
            SizedBox(
              width: double.infinity,
              child: FilledButton.icon(
                onPressed:
                    _adding || _added || _addOutcomeUnknown || !hasMissing
                    ? null
                    : _addMissingIngredients,
                icon: const Icon(Icons.playlist_add),
                label: Text(label),
              ),
            ),
            if (_addOutcomeUnknown)
              TextButton(
                onPressed: _confirmAnotherBatch,
                child: const Text('Add another batch…'),
              ),
          ],
        ),
      ),
    );
  }

  /**
   * Builds ingredient rows for the detail screen.
   * <p>
   * Uses ingredient details from the recipe when available.
   * Otherwise builds a local fallback list from ingredient tags.
   */
  List<_IngredientItem> _ingredientRows(RecipeRecommendation recipe) {
    final details = recipe.ingredientDetails;
    if (details != null && (details.isNotEmpty || widget.useLiveApi)) {
      return details
          .map(
            (d) => _IngredientItem(
              name: d.name,
              amount: d.quantityText.isEmpty ? '—' : d.quantityText,
              inFridge: d.inFridge,
            ),
          )
          .toList();
    }
    if (widget.useLiveApi) return const [];
    final rows = <_IngredientItem>[];
    for (final tag in recipe.ingredientTags) {
      rows.add(
        _IngredientItem(name: tag, amount: _amountFor(tag), inFridge: true),
      );
    }
    while (rows.length < 6) {
      final fillers = [
        const _IngredientItem(
          name: 'Whole Milk',
          amount: '2 tbsp',
          inFridge: true,
        ),
        const _IngredientItem(
          name: 'Unsalted Butter',
          amount: '10 g',
          inFridge: false,
        ),
        const _IngredientItem(
          name: 'Sourdough Bread',
          amount: '2 slices',
          inFridge: false,
        ),
      ];
      rows.add(
        fillers[(rows.length - recipe.ingredientTags.length) % fillers.length],
      );
    }
    return rows.take(6).toList();
  }

  /**
   * Returns a fallback amount label for an ingredient name.
   * <p>
   * Provides a default amount when detailed ingredient data is missing.
   */
  String _amountFor(String text) {
    switch (text.toLowerCase()) {
      case 'eggs':
      case 'free range eggs':
        return '3 pcs';
      case 'spinach':
      case 'baby spinach':
        return '50 g';
      case 'cheese':
      case 'cheddar cheese':
        return '30 g';
      case 'whole milk':
        return '2 tbsp';
      default:
        return '1 portion';
    }
  }

  /**
   * Returns a fallback kcal value for an ingredient name.
   * <p>
   * Provides a default kcal value when nutrition data is missing.
   */
  int _kcalFor(String text) {
    switch (text.toLowerCase()) {
      case 'eggs':
      case 'free range eggs':
        return 180;
      case 'spinach':
      case 'baby spinach':
        return 12;
      case 'cheese':
      case 'cheddar cheese':
        return 120;
      case 'whole milk':
        return 18;
      default:
        return 30;
    }
  }
}

/**
 * Desktop recipe info rail.
 * <p>
 * Left-side desktop panel with back navigation, tags, title,
 * description, summary cards, ingredient match, ingredient list,
 * and the main CTA.
 *
 * Official references:
 * ListView.separated:
 * https://api.flutter.dev/flutter/widgets/ListView/ListView.separated.html
 * TextButton:
 * https://api.flutter.dev/flutter/material/TextButton-class.html
 * FilledButton:
 * https://api.flutter.dev/flutter/material/FilledButton-class.html
 * Divider:
 * https://api.flutter.dev/flutter/material/Divider-class.html
 */
class _RecipeInfoRail extends StatelessWidget {
  const _RecipeInfoRail({
    required this.recipe,
    required this.match,
    required this.ingredients,
    required this.onBack,
    required this.isLive,
  });

  final RecipeRecommendation recipe;
  final int match;
  final List<_IngredientItem> ingredients;
  final VoidCallback onBack;
  final bool isLive;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 272,
      padding: const EdgeInsets.fromLTRB(18, 16, 18, 18),
      decoration: const BoxDecoration(
        color: SfColors.cream,
        border: Border(right: BorderSide(color: Color(0xFFEDE4D8))),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              TextButton.icon(
                onPressed: onBack,
                icon: const Icon(Icons.arrow_back_ios_new, size: 14),
                label: const Text('Back'),
                style: TextButton.styleFrom(
                  foregroundColor: SfColors.brownMuted,
                  padding: const EdgeInsets.symmetric(horizontal: 0),
                ),
              ),
              const SizedBox(width: 8),
              const Flexible(
                child: Text(
                  '· SmartFridge',
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(color: SfColors.brownMuted, fontSize: 12),
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Wrap(
            spacing: 8,
            runSpacing: 6,
            children: [
              _TagPill(label: recipe.slot.label, fill: SfColors.chipBgLight),
              _TagPill(
                label: '${recipe.matchPercent}% match',
                fill: const Color(0xFFEAF6EC),
                fg: SfColors.matchGreen,
              ),
            ],
          ),
          const SizedBox(height: 14),
          Text(
            recipe.title,
            style: const TextStyle(
              fontSize: 48,
              fontWeight: FontWeight.w800,
              color: SfColors.brown,
              height: 0.95,
            ),
          ),
          const SizedBox(height: 14),
          Text(
            (recipe.description != null &&
                    recipe.description!.trim().isNotEmpty)
                ? recipe.description!.trim()
                : isLive
                ? 'No preparation instructions provided.'
                : 'A rich, protein-packed breakfast made from ingredients already in your fridge.',
            style: TextStyle(
              color: SfColors.brownLabel.withValues(alpha: 0.86),
              fontSize: 13,
              height: 1.45,
            ),
          ),
          const SizedBox(height: 14),
          Row(
            children: [
              Expanded(
                child: _InfoMiniCard(
                  value: '${recipe.prepMinutes}',
                  unit: 'min',
                  label: 'Prep time',
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: _InfoMiniCard(
                  value: '${recipe.kcal}',
                  unit: 'kcal',
                  label: 'Calories',
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          _MatchCard(match: match),
          const SizedBox(height: 14),
          const Text(
            'INGREDIENTS',
            style: TextStyle(
              fontSize: 11,
              letterSpacing: 2.2,
              fontWeight: FontWeight.w700,
              color: SfColors.brownMuted,
            ),
          ),
          const SizedBox(height: 8),
          Expanded(
            child: ListView.separated(
              itemCount: ingredients.length,
              separatorBuilder: (context, index) =>
                  const Divider(height: 1, color: Color(0xFFE7DED1)),
              itemBuilder: (context, i) {
                final item = ingredients[i];
                return Padding(
                  padding: const EdgeInsets.symmetric(vertical: 8),
                  child: Row(
                    children: [
                      Icon(
                        Icons.circle,
                        size: 5,
                        color: item.inFridge
                            ? SfColors.brown
                            : SfColors.chipBgLight,
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          item.name,
                          style: TextStyle(
                            fontSize: 13,
                            fontWeight: FontWeight.w700,
                            color: item.inFridge
                                ? SfColors.brown
                                : SfColors.brownMuted,
                          ),
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      const SizedBox(width: 8),
                      Text(
                        item.amount,
                        style: TextStyle(
                          fontSize: 12,
                          color: item.inFridge
                              ? SfColors.brownLabel
                              : SfColors.brownMuted,
                          fontWeight: FontWeight.w500,
                        ),
                      ),
                      if (item.inFridge) ...[
                        const SizedBox(width: 6),
                        const Icon(
                          Icons.check,
                          size: 14,
                          color: SfColors.matchGreen,
                        ),
                      ],
                    ],
                  ),
                );
              },
            ),
          ),
          const SizedBox(height: 10),
          if (!isLive)
            SizedBox(
              width: double.infinity,
              child: FilledButton(
                style: FilledButton.styleFrom(
                  backgroundColor: SfColors.brown,
                  foregroundColor: Colors.white,
                  padding: const EdgeInsets.symmetric(vertical: 14),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(SfRadii.pill),
                  ),
                ),
                onPressed: () {},
                child: const Text(
                  'Start Cooking →',
                  style: TextStyle(fontWeight: FontWeight.w700),
                ),
              ),
            ),
        ],
      ),
    );
  }
}

/**
 * Mobile recipe detail layout.
 * <p>
 * Mobile detail layout built with a SliverAppBar and SliverList.
 * Shows summary tags, description, and ingredients.
 *
 * Official references:
 * CustomScrollView:
 * https://api.flutter.dev/flutter/widgets/CustomScrollView-class.html
 * SliverAppBar:
 * https://api.flutter.dev/flutter/material/SliverAppBar-class.html
 * SliverList:
 * https://api.flutter.dev/flutter/widgets/SliverList-class.html
 * ListTile:
 * https://api.flutter.dev/flutter/material/ListTile-class.html
 */
class _MobileRecipeDetail extends StatelessWidget {
  const _MobileRecipeDetail({
    required this.recipe,
    required this.saved,
    required this.onToggleSave,
    required this.ingredients,
    required this.match,
  });

  final RecipeRecommendation recipe;
  final bool saved;
  final VoidCallback onToggleSave;
  final List<_IngredientItem> ingredients;
  final int match;

  @override
  Widget build(BuildContext context) {
    return CustomScrollView(
      slivers: [
        SliverAppBar(
          pinned: true,
          backgroundColor: SfColors.cream,
          foregroundColor: SfColors.brown,
          title: Text(
            recipe.title,
            style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w800),
          ),
          actions: [
            IconButton(
              onPressed: onToggleSave,
              icon: Icon(saved ? Icons.bookmark : Icons.bookmark_border),
            ),
          ],
        ),
        SliverPadding(
          padding: const EdgeInsets.all(16),
          sliver: SliverList(
            delegate: SliverChildListDelegate([
              _MatchCard(match: match),
              const SizedBox(height: 12),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  _TagPill(
                    label: '${recipe.prepMinutes} min',
                    fill: SfColors.chipBgLight,
                  ),
                  _TagPill(
                    label: '${recipe.kcal} kcal',
                    fill: SfColors.chipBgLight,
                  ),
                  _TagPill(
                    label: '${recipe.matchPercent}% match',
                    fill: const Color(0xFFEAF6EC),
                    fg: SfColors.matchGreen,
                  ),
                ],
              ),
              const SizedBox(height: 16),
              if (recipe.description != null &&
                  recipe.description!.trim().isNotEmpty) ...[
                Text(
                  recipe.description!.trim(),
                  style: TextStyle(
                    fontSize: 14,
                    height: 1.45,
                    color: SfColors.brownLabel.withValues(alpha: 0.9),
                  ),
                ),
                const SizedBox(height: 16),
              ],
              const Text(
                'Ingredients',
                style: TextStyle(
                  fontWeight: FontWeight.w800,
                  color: SfColors.brown,
                ),
              ),
              const SizedBox(height: 8),
              ...ingredients.map(
                (e) => ListTile(
                  dense: true,
                  contentPadding: EdgeInsets.zero,
                  leading: Icon(
                    e.inFridge
                        ? Icons.check_circle
                        : Icons.radio_button_unchecked,
                    color: e.inFridge
                        ? SfColors.matchGreen
                        : SfColors.brownMuted,
                  ),
                  title: Text(e.name),
                  trailing: Text(e.amount),
                ),
              ),
            ]),
          ),
        ),
      ],
    );
  }
}

/**
 * Ingredient row model.
 * <p>
 * Lightweight ingredient model used inside the detail screen.
 */
class _IngredientItem {
  const _IngredientItem({
    required this.name,
    required this.amount,
    required this.inFridge,
  });

  final String name;
  final String amount;
  final bool inFridge;
}

/**
 * Small numeric info card.
 * <p>
 * Small summary card for values like prep time or calories.
 */
class _InfoMiniCard extends StatelessWidget {
  const _InfoMiniCard({
    required this.value,
    required this.unit,
    required this.label,
  });

  final String value;
  final String unit;
  final String label;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.fromLTRB(12, 10, 12, 8),
      decoration: BoxDecoration(
        color: SfColors.whiteCard,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: const Color(0xFFECE3D8)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          RichText(
            text: TextSpan(
              text: value,
              style: const TextStyle(
                color: SfColors.brown,
                fontWeight: FontWeight.w800,
                fontSize: 33,
                height: 1,
              ),
              children: [
                TextSpan(
                  text: ' $unit',
                  style: const TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.w600,
                    color: SfColors.brownMuted,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 2),
          Text(
            label,
            style: const TextStyle(fontSize: 12, color: SfColors.brownMuted),
          ),
        ],
      ),
    );
  }
}

/**
 * Ingredient match summary card.
 * <p>
 * Shows the ingredient match percentage for the current recipe.
 */
class _MatchCard extends StatelessWidget {
  const _MatchCard({required this.match});

  final int match;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.fromLTRB(12, 10, 12, 10),
      decoration: BoxDecoration(
        color: SfColors.chipBgLight.withValues(alpha: 0.7),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          RichText(
            text: TextSpan(
              text: '$match',
              style: const TextStyle(
                color: SfColors.brown,
                fontWeight: FontWeight.w800,
                fontSize: 32,
                height: 1,
              ),
              children: const [
                TextSpan(
                  text: '%',
                  style: TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w700,
                    color: SfColors.brownMuted,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 3),
          const Text(
            'Required ingredients available',
            style: TextStyle(fontSize: 12, color: SfColors.brownMuted),
          ),
        ],
      ),
    );
  }
}

/**
 * Reusable tag pill.
 * <p>
 * Reusable pill label for short tags like slot, match, or time.
 */
class _TagPill extends StatelessWidget {
  const _TagPill({
    required this.label,
    required this.fill,
    this.fg = SfColors.brown,
  });

  final String label;
  final Color fill;
  final Color fg;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 4),
      decoration: BoxDecoration(
        color: fill,
        borderRadius: BorderRadius.circular(SfRadii.pill),
      ),
      child: Text(
        label,
        style: TextStyle(fontSize: 11, fontWeight: FontWeight.w700, color: fg),
      ),
    );
  }
}

/**
 * Review pill.
 * <p>
 * Rating summary pill with stars, score, and review count.
 */
class _ReviewPill extends StatelessWidget {
  const _ReviewPill({required this.rating, required this.reviews});

  final double rating;
  final int reviews;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
      decoration: BoxDecoration(
        color: SfColors.whiteCard,
        borderRadius: BorderRadius.circular(SfRadii.pill),
        border: Border.all(color: const Color(0xFFEEE5DA)),
      ),
      child: Row(
        children: [
          ...List.generate(5, (i) {
            final on = i + 1 <= rating.round();
            return Icon(
              on ? Icons.star : Icons.star_border,
              size: 14,
              color: const Color(0xFFFFB300),
            );
          }),
          const SizedBox(width: 6),
          Text(
            rating.toStringAsFixed(1),
            style: const TextStyle(
              fontSize: 12,
              fontWeight: FontWeight.w800,
              color: SfColors.brown,
            ),
          ),
          const SizedBox(width: 4),
          Text(
            '$reviews reviews',
            style: const TextStyle(fontSize: 11, color: SfColors.brownMuted),
          ),
        ],
      ),
    );
  }
}

/**
 * Timeline pill.
 * <p>
 * Bottom summary pill for prep, cook, total time, and servings.
 */
class _TimelinePill extends StatelessWidget {
  const _TimelinePill({
    required this.prep,
    required this.cook,
    required this.total,
    required this.serves,
  });

  final int prep;
  final int cook;
  final int total;
  final int serves;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
      decoration: BoxDecoration(
        color: SfColors.whiteCard,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: const Color(0xFFEEE5DA)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          _TimeBlock(title: 'Prep', value: '$prep min'),
          _divider(),
          _TimeBlock(title: 'Cook', value: '$cook min'),
          _divider(),
          _TimeBlock(title: 'Total', value: '$total min'),
          _divider(),
          _TimeBlock(title: 'Serves', value: '$serves people'),
        ],
      ),
    );
  }

  Widget _divider() => Container(
    width: 1,
    height: 24,
    margin: const EdgeInsets.symmetric(horizontal: 8),
    color: const Color(0xFFE9E0D4),
  );
}

/**
 * Single time block inside timeline pill.
 * <p>
 * Single label-value block inside the timeline summary.
 */
class _TimeBlock extends StatelessWidget {
  const _TimeBlock({required this.title, required this.value});

  final String title;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          value,
          style: const TextStyle(
            color: SfColors.brown,
            fontSize: 13,
            fontWeight: FontWeight.w800,
          ),
        ),
        Text(
          title,
          style: const TextStyle(
            color: SfColors.brownMuted,
            fontSize: 10,
            fontWeight: FontWeight.w600,
          ),
        ),
      ],
    );
  }
}

/**
 * Circular icon button.
 * <p>
 * Round icon button used for actions like bookmark and share.
 *
 * Official references:
 * InkWell:
 * https://api.flutter.dev/flutter/material/InkWell-class.html
 * Container:
 * https://api.flutter.dev/flutter/widgets/Container-class.html
 */
class _CircleIconButton extends StatelessWidget {
  const _CircleIconButton({required this.icon, this.onTap});

  final IconData icon;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(999),
      child: Container(
        width: 34,
        height: 34,
        decoration: BoxDecoration(
          color: SfColors.whiteCard,
          shape: BoxShape.circle,
          border: Border.all(color: const Color(0xFFEEE5DA)),
        ),
        child: Icon(icon, size: 18, color: SfColors.brown),
      ),
    );
  }
}

/**
 * Ingredient tile.
 * <p>
 * Desktop ingredient tile with name, fridge status, and kcal summary.
 */
class _IngredientTile extends StatelessWidget {
  const _IngredientTile({required this.item, required this.kcal});

  final _IngredientItem item;
  final int? kcal;

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: const BoxDecoration(
        color: SfColors.whiteCard,
        border: Border(
          top: BorderSide(color: Color(0xFFECE2D7)),
          right: BorderSide(color: Color(0xFFECE2D7)),
        ),
      ),
      padding: const EdgeInsets.fromLTRB(14, 10, 14, 14),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Align(
            alignment: Alignment.center,
            child: Icon(
              Icons.circle,
              size: 6,
              color: item.inFridge ? SfColors.matchGreen : SfColors.chipBgLight,
            ),
          ),
          const SizedBox(height: 10),
          Text(
            item.name,
            style: const TextStyle(
              color: SfColors.brown,
              fontWeight: FontWeight.w800,
              fontSize: 22,
              height: 1,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            item.inFridge ? 'In fridge' : 'Need to buy',
            style: const TextStyle(color: SfColors.brownMuted, fontSize: 11),
          ),
          const Spacer(),
          Center(
            child: Container(
              width: 124,
              height: 124,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                border: Border.all(color: const Color(0xFFEEE4D8), width: 2),
              ),
            ),
          ),
          const Spacer(),
          Center(
            child: Text(
              kcal == null ? item.amount : '${item.amount} · $kcal kcal',
              style: const TextStyle(color: SfColors.brownMuted, fontSize: 12),
            ),
          ),
        ],
      ),
    );
  }
}
